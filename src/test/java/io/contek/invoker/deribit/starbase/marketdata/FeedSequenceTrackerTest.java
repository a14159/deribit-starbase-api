package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;

public final class FeedSequenceTrackerTest {

  private static volatile long sink;

  public void testInitializesAndAdvancesOnlyContiguousMessageRanges() {
    FeedSequenceTracker tracker = new FeedSequenceTracker();

    assertFalse(tracker.isInitialized());
    assertEquals(FeedSequenceTracker.INITIALIZED, tracker.accept(100L, 3));
    assertTrue(tracker.isInitialized());
    assertEquals(103L, tracker.nextExpectedSequence());
    assertEquals(FeedSequenceTracker.CONTIGUOUS, tracker.accept(103L, 2));
    assertEquals(105L, tracker.nextExpectedSequence());
  }

  public void testClassifiesDuplicatesAndGapsWithoutAdvancing() {
    FeedSequenceTracker tracker = new FeedSequenceTracker();
    tracker.accept(100L, 5);

    assertEquals(FeedSequenceTracker.DUPLICATE, tracker.accept(100L, 5));
    assertEquals(FeedSequenceTracker.DUPLICATE, tracker.accept(102L, 2));
    assertEquals(FeedSequenceTracker.GAP, tracker.accept(108L, 2));
    assertEquals(105L, tracker.nextExpectedSequence());
    assertEquals(3L, tracker.gapSize());
  }

  public void testZeroCountHeartbeatRetainsAdvertisedNextSequence() {
    FeedSequenceTracker tracker = new FeedSequenceTracker();

    assertEquals(FeedSequenceTracker.HEARTBEAT, tracker.accept(500L, 0));
    assertEquals(500L, tracker.nextExpectedSequence());
    assertEquals(FeedSequenceTracker.HEARTBEAT, tracker.accept(500L, 0));
    assertEquals(FeedSequenceTracker.DUPLICATE, tracker.accept(499L, 0));
    assertEquals(FeedSequenceTracker.GAP, tracker.accept(501L, 0));
    assertEquals(500L, tracker.nextExpectedSequence());
  }

  public void testInvalidRangesAndPartialOverlapFailClosed() {
    FeedSequenceTracker tracker = new FeedSequenceTracker();
    tracker.accept(10L, 5);

    assertThrows(IllegalArgumentException.class, () -> tracker.accept(-1L, 1));
    assertThrows(IllegalArgumentException.class, () -> tracker.accept(1L, -1));
    assertThrows(IllegalArgumentException.class, () -> tracker.accept(1L, 65_536));
    assertThrows(
        StarbaseProtocolException.class, () -> tracker.accept(Long.MAX_VALUE, 1));
    assertThrows(StarbaseProtocolException.class, () -> tracker.accept(14L, 2));
  }

  public void testResetDropsPriorSequenceDomain() {
    FeedSequenceTracker tracker = new FeedSequenceTracker();
    tracker.accept(42L, 1);

    tracker.reset();

    assertFalse(tracker.isInitialized());
    assertEquals(FeedSequenceTracker.INITIALIZED, tracker.accept(7L, 1));
    assertEquals(8L, tracker.nextExpectedSequence());
  }

  public void testRecoveredGapAdvancesTheAffectedFeedPastItsHeldPacket() {
    FeedSequenceTracker tracker = new FeedSequenceTracker();
    tracker.accept(10L, 1);
    assertEquals(FeedSequenceTracker.GAP, tracker.accept(12L, 2));

    tracker.advanceAfterGap(12L, 2);

    assertEquals(14L, tracker.nextExpectedSequence());
    assertEquals(FeedSequenceTracker.CONTIGUOUS, tracker.accept(14L, 1));
    assertThrows(
        IllegalStateException.class,
        () -> tracker.advanceAfterGap(20L, 1));
  }

  public void testNormalSequenceTrackingAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    FeedSequenceTracker tracker = new FeedSequenceTracker();
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(tracker, iteration);
    }
    tracker.reset();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(tracker, iteration);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated);
  }

  private static void exercise(FeedSequenceTracker tracker, int iteration) {
    if (iteration == 0) {
      sink += tracker.accept(0L, 1);
    } else {
      sink += tracker.accept(iteration, 1);
    }
  }
}
