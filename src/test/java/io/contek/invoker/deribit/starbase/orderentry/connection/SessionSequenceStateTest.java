package io.contek.invoker.deribit.starbase.orderentry.connection;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;

public final class SessionSequenceStateTest {

  private static volatile long sink;

  public void testInboundGapExposesTheExactInclusiveResendRangeWithoutAdvancing() {
    SessionSequenceState sequences = new SessionSequenceState(1, 1);

    assertEquals(SessionSequenceState.ACTION_ACCEPT, sequences.onInbound(1, 0, false));
    assertEquals(2, sequences.nextExpectedInbound());

    assertEquals(SessionSequenceState.ACTION_RESEND, sequences.onInbound(3, 0, false));
    assertEquals(2, sequences.resendFromSequence());
    assertEquals(2, sequences.resendToSequence());
    assertEquals(2, sequences.nextExpectedInbound());
  }

  public void testResentMessagesCloseTheOutstandingGapAndDuplicatesNeverAdvance() {
    SessionSequenceState sequences = new SessionSequenceState(1, 1);
    assertEquals(SessionSequenceState.ACTION_ACCEPT, sequences.onInbound(1, 0, false));
    assertEquals(SessionSequenceState.ACTION_RESEND, sequences.onInbound(4, 0, false));
    assertEquals(SessionSequenceState.ACTION_ACCEPT, sequences.onInbound(2, 0, true));
    assertEquals(3, sequences.resendFromSequence());
    assertEquals(3, sequences.resendToSequence());
    assertEquals(SessionSequenceState.ACTION_DUPLICATE, sequences.onInbound(2, 0, true));
    assertEquals(3, sequences.nextExpectedInbound());
    assertEquals(SessionSequenceState.ACTION_ACCEPT, sequences.onInbound(3, 0, true));
    assertEquals(0, sequences.resendFromSequence());
    assertEquals(SessionSequenceState.ACTION_ACCEPT, sequences.onInbound(4, 0, false));
    assertEquals(5, sequences.nextExpectedInbound());
  }

  public void testOutboundClaimsAndPeerAcknowledgmentsAreExactAndMonotonic() {
    SessionSequenceState sequences = new SessionSequenceState(1, 5);
    assertEquals(5, sequences.claimOutboundSequence());
    assertEquals(6, sequences.claimOutboundSequence());
    assertEquals(7, sequences.nextOutbound());
    assertEquals(SessionSequenceState.ACTION_ACCEPT, sequences.onInbound(1, 5, false));
    assertEquals(5, sequences.lastPeerAcknowledgment());
    assertEquals(1, sequences.lastProcessedInbound());
    assertEquals(SessionSequenceState.ACTION_ACCEPT, sequences.onInbound(2, 6, false));

    assertThrows(
        StarbaseProtocolException.class, () -> sequences.onInbound(3, 5, false));
    assertTrue(sequences.isFailed());
    assertThrows(IllegalStateException.class, sequences::claimOutboundSequence);
  }

  public void testAcknowledgmentCannotCoverUnsentOutboundSequence() {
    SessionSequenceState sequences = new SessionSequenceState(1, 1);
    assertThrows(
        StarbaseProtocolException.class, () -> sequences.onInbound(1, 1, false));
    assertTrue(sequences.isFailed());
  }

  public void testGapFillMovesForwardAndResetClearsAllRecoveryAndFailureState() {
    SessionSequenceState sequences = new SessionSequenceState(5, 7);
    assertEquals(
        SessionSequenceState.ACTION_ACCEPT,
        sequences.onGapFill(5, 0, false, 10));
    assertEquals(10, sequences.nextExpectedInbound());
    assertThrows(
        StarbaseProtocolException.class,
        () -> sequences.onGapFill(10, 0, false, 9));
    assertTrue(sequences.isFailed());

    sequences.reset(20, 30);
    assertFalse(sequences.isFailed());
    assertEquals(20, sequences.nextExpectedInbound());
    assertEquals(30, sequences.nextOutbound());
    assertEquals(0, sequences.lastPeerAcknowledgment());
    assertEquals(SessionSequenceState.ACTION_ACCEPT, sequences.onInbound(20, 0, false));
  }

  public void testPeerResendRangeIsBoundedBySentMessagesAndReplaysOriginalSequences() {
    SessionSequenceState sequences = new SessionSequenceState(1, 1);
    assertEquals(1, sequences.claimOutboundSequence());
    assertEquals(2, sequences.claimOutboundSequence());
    assertEquals(3, sequences.claimOutboundSequence());
    assertEquals(
        SessionSequenceState.ACTION_REPLAY,
        sequences.onPeerResendRequest(1, 0, false, 1, 0));
    assertEquals(1, sequences.replayFromSequence());
    assertEquals(3, sequences.replayToSequence());
    assertEquals(1, sequences.claimReplaySequence());
    assertEquals(2, sequences.claimReplaySequence());
    assertEquals(3, sequences.claimReplaySequence());
    assertEquals(4, sequences.nextOutbound());
    assertThrows(IllegalStateException.class, sequences::claimReplaySequence);

    SessionSequenceState invalid = new SessionSequenceState(1, 1);
    invalid.claimOutboundSequence();
    assertThrows(
        StarbaseProtocolException.class,
        () -> invalid.onPeerResendRequest(1, 0, false, 1, 2));
  }

  public void testSequenceOverflowFailsClosed() {
    SessionSequenceState inbound = new SessionSequenceState(Long.MAX_VALUE, 1);
    assertThrows(
        StarbaseProtocolException.class,
        () -> inbound.onInbound(Long.MAX_VALUE, 0, false));
    SessionSequenceState outbound = new SessionSequenceState(1, Long.MAX_VALUE);
    assertThrows(StarbaseProtocolException.class, outbound::claimOutboundSequence);
  }

  public void testNormalSequenceTrackingAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    SessionSequenceState sequences = new SessionSequenceState(1, 1);
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      exercise(sequences);
    }
    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(sequences);
    }
    assertEquals(
        0L,
        bean.getThreadAllocatedBytes(threadId) - before,
        "session sequence hot path allocated bytes");
  }

  private static void exercise(SessionSequenceState sequences) {
    long outbound = sequences.claimOutboundSequence();
    long inbound = sequences.nextExpectedInbound();
    sink += sequences.onInbound(inbound, outbound, false);
  }
}
