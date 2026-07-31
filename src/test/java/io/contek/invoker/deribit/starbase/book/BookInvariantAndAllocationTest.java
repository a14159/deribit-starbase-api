package io.contek.invoker.deribit.starbase.book;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidPutDecoder;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;

public final class BookInvariantAndAllocationTest {

  private static volatile long sink;

  public void testValidatesMutatedAndAtomicallyReplayedBooks() {
    AggregatedL3Book book = book(8, 8);
    book.put(1L, 100L, BidPutDecoder.SIDE, 10L, 100L, 2L);
    book.put(2L, 100L, BidPutDecoder.SIDE, 20L, 100L, 1L);
    book.reduce(1L, 100L, BidPutDecoder.SIDE, 4L);
    book.delete(2L, 100L, BidPutDecoder.SIDE);
    book.validateInvariants();

    AtomicBookSnapshot snapshots = snapshots();
    snapshots.beginSnapshot(10L);
    snapshots.snapshotPut(3L, 100L, BidPutDecoder.SIDE, 3L, 101L, 3L);
    snapshots.bufferPut(11L, 4L, 100L, BidPutDecoder.SIDE, 4L, 101L, 4L);
    snapshots.completeSnapshot(10L);
    snapshots.activeBook().validateInvariants();
  }

  public void testDetectsCorruptAggregateState() throws ReflectiveOperationException {
    AggregatedL3Book book = book(4, 4);
    book.put(1L, 100L, BidPutDecoder.SIDE, 10L, 100L, 1L);
    Field levelsField = AggregatedL3Book.class.getDeclaredField("levels");
    levelsField.setAccessible(true);
    Object levels = levelsField.get(book);
    Field quantitiesField = PriceLevelStore.class.getDeclaredField("quantities");
    quantitiesField.setAccessible(true);
    long[] quantities = (long[]) quantitiesField.get(levels);
    for (int slot = 0; slot < quantities.length; slot++) {
      if (quantities[slot] != 0) {
        quantities[slot]++;
        break;
      }
    }

    assertThrows(StarbaseProtocolException.class, book::validateInvariants);
  }

  public void testAddReduceDeleteCycleAllocatesNothingAfterWarmup() {
    AggregatedL3Book book = book(2, 2);
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      sink += book.put(1L, 100L, BidPutDecoder.SIDE, 2L, 100L, 1L);
      sink += book.reduce(1L, 100L, BidPutDecoder.SIDE, 1L);
      sink += book.delete(1L, 100L, BidPutDecoder.SIDE);
    }
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      sink += book.put(1L, 100L, BidPutDecoder.SIDE, 2L, 100L, 1L);
      sink += book.reduce(1L, 100L, BidPutDecoder.SIDE, 1L);
      sink += book.delete(1L, 100L, BidPutDecoder.SIDE);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    book.validateInvariants();
    assertEquals(0L, allocated);
  }

  private static AtomicBookSnapshot snapshots() {
    InstrumentRegistry registry = registry();
    return new AtomicBookSnapshot(8, 8, 8, registry);
  }

  private static AggregatedL3Book book(int orders, int levels) {
    return new AggregatedL3Book(orders, levels, registry());
  }

  private static InstrumentRegistry registry() {
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
    return registry;
  }
}
