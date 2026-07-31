package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;

public final class SnapshotSynchronizationTest {

  private static volatile long sink;

  public void testSnapshotAnchorDropsOverlapAndRequiresContiguousBufferedReplay() {
    SnapshotSynchronization sync = new SnapshotSynchronization();

    assertEquals(SnapshotSynchronization.STARTED, sync.beginSnapshot(100L));
    assertEquals(SnapshotSynchronization.IGNORE, sync.onIncremental(99L));
    assertEquals(SnapshotSynchronization.IGNORE, sync.onIncremental(100L));
    assertEquals(SnapshotSynchronization.BUFFER, sync.onIncremental(101L));
    assertEquals(SnapshotSynchronization.BUFFER, sync.onIncremental(102L));
    assertEquals(SnapshotSynchronization.REPLAY_REQUIRED, sync.completeSnapshot(100L));
    assertFalse(sync.isLive());
    assertEquals(SnapshotSynchronization.LIVE, sync.completeReplay(103L));
    assertTrue(sync.isLive());
    assertEquals(103L, sync.nextExpectedSequence());
  }

  public void testSnapshotWithoutOverlapBecomesLiveAtAnchorPlusOne() {
    SnapshotSynchronization sync = new SnapshotSynchronization();

    sync.beginSnapshot(500L);

    assertEquals(SnapshotSynchronization.LIVE, sync.completeSnapshot(500L));
    assertTrue(sync.isLive());
    assertEquals(501L, sync.nextExpectedSequence());
  }

  public void testNewerSnapshotRestartsIncompleteOrUnhealthySynchronization() {
    SnapshotSynchronization sync = new SnapshotSynchronization();
    sync.beginSnapshot(10L);
    sync.onIncremental(11L);

    assertEquals(SnapshotSynchronization.RESTARTED, sync.beginSnapshot(20L));
    assertEquals(SnapshotSynchronization.LIVE, sync.completeSnapshot(20L));
    assertEquals(21L, sync.nextExpectedSequence());

    assertEquals(SnapshotSynchronization.RESTARTED, sync.beginSnapshot(30L));
    assertFalse(sync.isLive());
  }

  public void testLiveGapWaitsForExactRetransmitRangeThenRetriesHeldPacket() {
    SnapshotSynchronization sync = liveAt(100L);

    assertEquals(SnapshotSynchronization.APPLY, sync.onIncremental(100L));
    assertEquals(SnapshotSynchronization.RECOVER, sync.onIncremental(104L));
    assertEquals(101L, sync.missingBeginSequence());
    assertEquals(3L, sync.missingMessageCount());
    assertEquals(SnapshotSynchronization.RETRY_HELD, sync.retransmitComplete(104L));
    assertEquals(SnapshotSynchronization.APPLY, sync.onIncremental(104L));
    assertEquals(105L, sync.nextExpectedSequence());
  }

  public void testFailedOrMismatchedRecoveryRequiresFreshSnapshot() {
    SnapshotSynchronization sync = liveAt(50L);
    sync.onIncremental(55L);

    assertThrows(
        StarbaseProtocolException.class, () -> sync.retransmitComplete(54L));
    assertEquals(
        SnapshotSynchronization.FRESH_SNAPSHOT_REQUIRED, sync.retransmitFailed());
    assertTrue(sync.needsFreshSnapshot());
    assertEquals(
        SnapshotSynchronization.FRESH_SNAPSHOT_REQUIRED, sync.onIncremental(50L));
  }

  public void testInvalidBoundariesAndBufferedGapsFailClosedOrRecover() {
    SnapshotSynchronization sync = new SnapshotSynchronization();
    assertThrows(
        StarbaseProtocolException.class, () -> sync.completeSnapshot(1L));
    assertThrows(IllegalArgumentException.class, () -> sync.beginSnapshot(-1L));
    assertThrows(
        IllegalArgumentException.class, () -> sync.beginSnapshot(Long.MAX_VALUE));

    sync.beginSnapshot(10L);
    sync.onIncremental(12L);
    assertEquals(SnapshotSynchronization.RECOVER, sync.completeSnapshot(10L));
    assertEquals(11L, sync.missingBeginSequence());
    assertEquals(1L, sync.missingMessageCount());
  }

  public void testLiveSynchronizationAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    SnapshotSynchronization sync = liveAt(1L);
    for (int iteration = 1; iteration <= 100_000; iteration++) {
      sink += sync.onIncremental(iteration);
    }
    sync = liveAt(1L);
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 1; iteration <= 100_000; iteration++) {
      sink += sync.onIncremental(iteration);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated);
  }

  private static SnapshotSynchronization liveAt(long nextSequence) {
    SnapshotSynchronization sync = new SnapshotSynchronization();
    sync.beginSnapshot(nextSequence - 1);
    sync.completeSnapshot(nextSequence - 1);
    return sync;
  }
}
