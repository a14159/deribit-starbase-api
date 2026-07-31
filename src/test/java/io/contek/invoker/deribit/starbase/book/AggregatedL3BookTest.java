package io.contek.invoker.deribit.starbase.book;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.marketdata.AskPutDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidPutDecoder;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import java.lang.management.ManagementFactory;

public final class AggregatedL3BookTest {

  private static volatile long sink;

  public void testAggregatesSamePriceAndRetainsMinimumSortPriority() {
    AggregatedL3Book book = book(8, 8);

    book.put(1L, 100L, BidPutDecoder.SIDE, 10L, 1_000L, 50L);
    book.put(2L, 100L, BidPutDecoder.SIDE, 20L, 1_000L, 40L);
    book.put(3L, 100L, AskPutDecoder.SIDE, 30L, 1_000L, 30L);

    assertEquals(30L, book.levelQuantity(100L, BidPutDecoder.SIDE, 1_000L));
    assertEquals(2, book.levelOrderCount(100L, BidPutDecoder.SIDE, 1_000L));
    assertEquals(40L, book.levelFirstSortOrderId(100L, BidPutDecoder.SIDE, 1_000L));
    assertEquals(30L, book.levelQuantity(100L, AskPutDecoder.SIDE, 1_000L));
    assertEquals(1, book.levelOrderCount(100L, AskPutDecoder.SIDE, 1_000L));
  }

  public void testPriceMoveReductionAndDeleteUpdateOnlyAffectedLevels() {
    AggregatedL3Book book = book(8, 8);
    book.put(1L, 100L, BidPutDecoder.SIDE, 10L, 1_000L, 10L);
    book.put(2L, 100L, BidPutDecoder.SIDE, 20L, 1_000L, 20L);

    book.put(1L, 100L, BidPutDecoder.SIDE, 7L, 1_100L, 30L);
    assertEquals(20L, book.levelQuantity(100L, BidPutDecoder.SIDE, 1_000L));
    assertEquals(20L, book.levelFirstSortOrderId(100L, BidPutDecoder.SIDE, 1_000L));
    assertEquals(7L, book.levelQuantity(100L, BidPutDecoder.SIDE, 1_100L));
    book.reduce(2L, 100L, BidPutDecoder.SIDE, 5L);
    assertEquals(5L, book.levelQuantity(100L, BidPutDecoder.SIDE, 1_000L));
    book.delete(2L, 100L, BidPutDecoder.SIDE);
    assertFalse(book.hasLevel(100L, BidPutDecoder.SIDE, 1_000L));
  }

  public void testBestPricesRespectSideAndExactPrice9Ordering() {
    AggregatedL3Book book = book(8, 8);
    book.put(1L, 100L, BidPutDecoder.SIDE, 1L, -500L, 1L);
    book.put(2L, 100L, BidPutDecoder.SIDE, 1L, -400L, 2L);
    book.put(3L, 100L, AskPutDecoder.SIDE, 1L, -300L, 3L);
    book.put(4L, 100L, AskPutDecoder.SIDE, 1L, -200L, 4L);

    assertEquals(-400L, book.bestPriceMantissa(100L, BidPutDecoder.SIDE));
    assertEquals(-300L, book.bestPriceMantissa(100L, AskPutDecoder.SIDE));
  }

  public void testRemovingFirstPriorityRecomputesFromRemainingOrders() {
    AggregatedL3Book book = book(8, 8);
    book.put(1L, 100L, BidPutDecoder.SIDE, 1L, 10L, 100L);
    book.put(2L, 100L, BidPutDecoder.SIDE, 1L, 10L, 50L);
    book.put(3L, 100L, BidPutDecoder.SIDE, 1L, 10L, 75L);

    book.delete(2L, 100L, BidPutDecoder.SIDE);

    assertEquals(75L, book.levelFirstSortOrderId(100L, BidPutDecoder.SIDE, 10L));
  }

  public void testOverflowAndLevelCapacityFailWithoutCorruptingOrdersOrLevels() {
    AggregatedL3Book overflow = book(4, 4);
    overflow.put(1L, 100L, BidPutDecoder.SIDE, Long.MAX_VALUE, 10L, 1L);
    assertThrows(
        ArithmeticException.class,
        () -> overflow.put(2L, 100L, BidPutDecoder.SIDE, 1L, 10L, 2L));
    assertFalse(overflow.containsOrder(2L));
    assertEquals(Long.MAX_VALUE, overflow.levelQuantity(100L, BidPutDecoder.SIDE, 10L));

    AggregatedL3Book capacity = book(4, 1);
    capacity.put(1L, 100L, BidPutDecoder.SIDE, 1L, 10L, 1L);
    assertThrows(
        IllegalStateException.class,
        () -> capacity.put(2L, 100L, BidPutDecoder.SIDE, 1L, 11L, 2L));
    assertFalse(capacity.containsOrder(2L));
    assertEquals(1, capacity.levelCount());
  }

  public void testSameLevelAmendAllocatesNothingAfterWarmup() {
    AggregatedL3Book book = book(2, 2);
    book.put(1L, 100L, BidPutDecoder.SIDE, 1L, 10L, 1L);
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      sink += book.put(1L, 100L, BidPutDecoder.SIDE, iteration + 1L, 10L, iteration + 1L);
    }
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      sink += book.put(1L, 100L, BidPutDecoder.SIDE, iteration + 2L, 10L, iteration + 2L);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated);
  }

  private static AggregatedL3Book book(int orderCapacity, int levelCapacity) {
    InstrumentRegistry registry = new InstrumentRegistry(1);
    registry.upsert(
        100L,
        "BTC-PERPETUAL",
        ProductGroup.BTC,
        "BTC",
        "USD",
        -8,
        1L,
        1L,
        0,
        0,
        1);
    return new AggregatedL3Book(orderCapacity, levelCapacity, registry);
  }
}
