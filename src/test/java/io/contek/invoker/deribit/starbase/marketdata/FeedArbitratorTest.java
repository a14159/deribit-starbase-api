package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class FeedArbitratorTest {

  private static volatile long sink;

  public void testEarliestCopyWinsAndOtherFeedCopyIsDuplicate() {
    FeedArbitrator arbitrator = new FeedArbitrator();

    assertEquals(
        FeedArbitrator.ACCEPTED, arbitrator.accept(FeedArbitrator.SOURCE_A, 100L));
    assertEquals(
        FeedArbitrator.DUPLICATE, arbitrator.accept(FeedArbitrator.SOURCE_B, 100L));
    assertEquals(101L, arbitrator.nextExpectedSequence());
    assertEquals(FeedArbitrator.SOURCE_A, arbitrator.lastAcceptedSource());
  }

  public void testEitherFeedCanWinAndOneSideLossDoesNotInterruptContiguousProgress() {
    FeedArbitrator arbitrator = new FeedArbitrator();
    arbitrator.accept(FeedArbitrator.SOURCE_B, 40L);

    assertEquals(
        FeedArbitrator.ACCEPTED, arbitrator.accept(FeedArbitrator.SOURCE_A, 41L));
    assertEquals(
        FeedArbitrator.ACCEPTED, arbitrator.accept(FeedArbitrator.SOURCE_A, 42L));
    assertEquals(
        FeedArbitrator.ACCEPTED, arbitrator.accept(FeedArbitrator.SOURCE_A, 43L));
    assertEquals(44L, arbitrator.nextExpectedSequence());
  }

  public void testAheadCopyDoesNotAdvanceAndOtherSideCanFillReorderingGap() {
    FeedArbitrator arbitrator = new FeedArbitrator();
    arbitrator.accept(FeedArbitrator.SOURCE_A, 10L);

    assertEquals(FeedArbitrator.GAP, arbitrator.accept(FeedArbitrator.SOURCE_A, 12L));
    assertEquals(11L, arbitrator.nextExpectedSequence());
    assertEquals(1L, arbitrator.gapSize());
    assertEquals(
        FeedArbitrator.ACCEPTED, arbitrator.accept(FeedArbitrator.SOURCE_B, 11L));
    assertEquals(
        FeedArbitrator.ACCEPTED, arbitrator.accept(FeedArbitrator.SOURCE_A, 12L));
    assertEquals(13L, arbitrator.nextExpectedSequence());
  }

  public void testRecoveredSideProducesDuplicatesUntilItCatchesGlobalCursor() {
    FeedArbitrator arbitrator = new FeedArbitrator();
    arbitrator.accept(FeedArbitrator.SOURCE_A, 1000L);
    arbitrator.accept(FeedArbitrator.SOURCE_A, 1001L);
    arbitrator.accept(FeedArbitrator.SOURCE_A, 1002L);

    assertEquals(
        FeedArbitrator.DUPLICATE, arbitrator.accept(FeedArbitrator.SOURCE_B, 1001L));
    assertEquals(
        FeedArbitrator.DUPLICATE, arbitrator.accept(FeedArbitrator.SOURCE_B, 1002L));
    assertEquals(
        FeedArbitrator.ACCEPTED, arbitrator.accept(FeedArbitrator.SOURCE_B, 1003L));
    assertEquals(FeedArbitrator.SOURCE_B, arbitrator.lastAcceptedSource());
  }

  public void testInvalidSourceSequenceAndWrapFailClosed() {
    FeedArbitrator arbitrator = new FeedArbitrator();

    assertThrows(IllegalArgumentException.class, () -> arbitrator.accept(0, 1L));
    assertThrows(
        IllegalArgumentException.class,
        () -> arbitrator.accept(FeedArbitrator.SOURCE_A, -1L));
    assertThrows(
        IllegalArgumentException.class,
        () -> arbitrator.accept(FeedArbitrator.SOURCE_A, Long.MAX_VALUE));
  }

  public void testSimultaneousCopiesProduceExactlyOneWinner() throws Exception {
    FeedArbitrator arbitrator = new FeedArbitrator();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger accepted = new AtomicInteger();
    AtomicInteger duplicate = new AtomicInteger();
    Thread a = contender(arbitrator, FeedArbitrator.SOURCE_A, ready, start, accepted, duplicate);
    Thread b = contender(arbitrator, FeedArbitrator.SOURCE_B, ready, start, accepted, duplicate);
    a.start();
    b.start();
    ready.await();
    start.countDown();
    a.join();
    b.join();

    assertEquals(1, accepted.get());
    assertEquals(1, duplicate.get());
  }

  public void testResetStartsANewSequenceDomain() {
    FeedArbitrator arbitrator = new FeedArbitrator();
    arbitrator.accept(FeedArbitrator.SOURCE_A, 50L);

    arbitrator.reset();

    assertEquals(
        FeedArbitrator.ACCEPTED, arbitrator.accept(FeedArbitrator.SOURCE_B, 7L));
    assertEquals(8L, arbitrator.nextExpectedSequence());
  }

  public void testArbitrationAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      throw new AssertionError("thread allocation measurement is unsupported");
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    long threadId = Thread.currentThread().threadId();
    FeedArbitrator arbitrator = new FeedArbitrator();
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(arbitrator, iteration);
    }
    arbitrator.reset();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(arbitrator, iteration);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated);
  }

  private static void exercise(FeedArbitrator arbitrator, int iteration) {
    int source = (iteration & 1) == 0 ? FeedArbitrator.SOURCE_A : FeedArbitrator.SOURCE_B;
    sink += arbitrator.accept(source, iteration);
  }

  private static Thread contender(
      FeedArbitrator arbitrator,
      int source,
      CountDownLatch ready,
      CountDownLatch start,
      AtomicInteger accepted,
      AtomicInteger duplicate) {
    return Thread.ofPlatform()
        .unstarted(
            () -> {
              ready.countDown();
              try {
                start.await();
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
              }
              int result = arbitrator.accept(source, 77L);
              if (result == FeedArbitrator.ACCEPTED) {
                accepted.incrementAndGet();
              } else if (result == FeedArbitrator.DUPLICATE) {
                duplicate.incrementAndGet();
              }
            });
  }
}
