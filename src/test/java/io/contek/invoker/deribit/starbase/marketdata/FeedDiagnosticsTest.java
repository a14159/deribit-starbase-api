package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

public final class FeedDiagnosticsTest {

  private static volatile long sink;

  public void testReadinessRequiresTransportSnapshotAndClosedGapAndTransaction() {
    FeedDiagnostics diagnostics = new FeedDiagnostics();
    assertEquals(FeedDiagnostics.STARTING, diagnostics.health());
    assertFalse(diagnostics.isReady());

    diagnostics.onTransportOpen();
    assertEquals(FeedDiagnostics.SYNCHRONIZING, diagnostics.health());
    diagnostics.onSnapshotComplete();
    assertEquals(FeedDiagnostics.LIVE, diagnostics.health());
    assertTrue(diagnostics.isReady());

    diagnostics.onTransactionStart();
    assertFalse(diagnostics.isReady());
    diagnostics.onTransactionEnd();
    assertTrue(diagnostics.isReady());
    diagnostics.onGap();
    assertEquals(FeedDiagnostics.RECOVERING, diagnostics.health());
    assertFalse(diagnostics.isReady());
    diagnostics.onGapRecovered();
    assertTrue(diagnostics.isReady());
  }

  public void testFatalProtocolFailureAndCloseCannotAppearReady() {
    FeedDiagnostics diagnostics = live();

    diagnostics.onCorruptFrame();
    assertEquals(FeedDiagnostics.UNHEALTHY, diagnostics.health());
    assertFalse(diagnostics.isReady());
    assertEquals(1L, diagnostics.corruptFrames());

    diagnostics.onTransportClosed();
    assertEquals(FeedDiagnostics.CLOSED, diagnostics.health());
    assertFalse(diagnostics.isReady());
  }

  public void testSnapshotResetClearsReadinessUntilReplacementCompletes() {
    FeedDiagnostics diagnostics = live();

    diagnostics.onSnapshotReset();

    assertEquals(FeedDiagnostics.SYNCHRONIZING, diagnostics.health());
    assertFalse(diagnostics.isReady());
    assertEquals(1L, diagnostics.snapshotResets());
    diagnostics.onSnapshotComplete();
    assertTrue(diagnostics.isReady());
  }

  public void testAllRequiredCountersAreFixedAndSaturating() {
    FeedDiagnostics diagnostics = new FeedDiagnostics();

    diagnostics.onPacket(3);
    diagnostics.onDuplicate();
    diagnostics.onGap();
    diagnostics.onRetransmitRequest();
    diagnostics.onRetransmitReject();
    diagnostics.onUnknownTemplate();
    diagnostics.onReconnect();
    diagnostics.onCallbackFailure();
    diagnostics.onBufferExhaustion();
    diagnostics.addPackets(Long.MAX_VALUE, Long.MAX_VALUE);
    diagnostics.onPacket(1);

    assertEquals(Long.MAX_VALUE, diagnostics.packets());
    assertEquals(Long.MAX_VALUE, diagnostics.messages());
    assertEquals(1L, diagnostics.duplicates());
    assertEquals(1L, diagnostics.gaps());
    assertEquals(1L, diagnostics.retransmitRequests());
    assertEquals(1L, diagnostics.retransmitRejects());
    assertEquals(1L, diagnostics.unknownTemplates());
    assertEquals(1L, diagnostics.reconnects());
    assertEquals(1L, diagnostics.callbackFailures());
    assertEquals(1L, diagnostics.bufferExhaustions());
  }

  public void testResetCountersDoesNotForgeReadiness() {
    FeedDiagnostics diagnostics = live();
    diagnostics.onPacket(2);
    diagnostics.onGap();

    diagnostics.resetCounters();

    assertEquals(0L, diagnostics.packets());
    assertEquals(0L, diagnostics.gaps());
    assertFalse(diagnostics.isReady(), "counter reset must not resolve a real gap");
  }

  public void testNormalCounterAndHealthUpdatesAllocateNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    FeedDiagnostics diagnostics = live();
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(diagnostics);
    }
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(diagnostics);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated);
  }

  private static FeedDiagnostics live() {
    FeedDiagnostics diagnostics = new FeedDiagnostics();
    diagnostics.onTransportOpen();
    diagnostics.onSnapshotComplete();
    return diagnostics;
  }

  private static void exercise(FeedDiagnostics diagnostics) {
    diagnostics.onPacket(1);
    diagnostics.onDuplicate();
    sink += diagnostics.packets();
  }
}
