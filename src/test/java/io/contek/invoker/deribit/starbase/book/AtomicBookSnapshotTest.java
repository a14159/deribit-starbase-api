package io.contek.invoker.deribit.starbase.book;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertSame;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import io.contek.invoker.deribit.starbase.codec.marketdata.BidPutDecoder;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;

public final class AtomicBookSnapshotTest {

  public void testInitialSnapshotAndBufferedIncrementalsBecomeVisibleAtomically() {
    AtomicBookSnapshot books = books();
    AggregatedL3Book before = books.activeBook();

    books.beginSnapshot(100L);
    books.snapshotPut(1L, 100L, BidPutDecoder.SIDE, 10L, 1_000L, 1L);
    books.bufferPut(101L, 2L, 100L, BidPutDecoder.SIDE, 20L, 1_000L, 2L);

    assertSame(before, books.activeBook());
    assertFalse(before.containsOrder(1L));

    books.completeSnapshot(100L);

    assertEquals(30L, books.activeBook().levelQuantity(100L, BidPutDecoder.SIDE, 1_000L));
    assertEquals(102L, books.nextExpectedSequence());
    assertEquals(1L, books.publicationVersion());
  }

  public void testReplacementAndFailedSnapshotsRetainLastPublishedBook() {
    AtomicBookSnapshot books = books();
    books.beginSnapshot(10L);
    books.snapshotPut(1L, 100L, BidPutDecoder.SIDE, 1L, 10L, 1L);
    books.completeSnapshot(10L);
    AggregatedL3Book published = books.activeBook();

    books.beginSnapshot(20L);
    books.snapshotPut(2L, 100L, BidPutDecoder.SIDE, 2L, 20L, 2L);
    assertSame(published, books.activeBook());
    books.failSnapshot();

    assertSame(published, books.activeBook());
    assertEquals(1L, books.publicationVersion());
    assertThrows(StarbaseProtocolException.class, () -> books.completeSnapshot(20L));
  }

  public void testReplacementDropsOldStateAndReplaysAllMutationKinds() {
    AtomicBookSnapshot books = books();
    books.beginSnapshot(10L);
    books.snapshotPut(9L, 100L, BidPutDecoder.SIDE, 9L, 90L, 9L);
    books.completeSnapshot(10L);

    books.beginSnapshot(20L);
    books.snapshotPut(1L, 100L, BidPutDecoder.SIDE, 10L, 100L, 1L);
    books.snapshotPut(2L, 100L, BidPutDecoder.SIDE, 20L, 100L, 2L);
    books.bufferReduce(21L, 1L, 100L, BidPutDecoder.SIDE, 4L);
    books.bufferDelete(22L, 2L, 100L, BidPutDecoder.SIDE);
    books.bufferPut(23L, 3L, 100L, BidPutDecoder.SIDE, 3L, 101L, 3L);
    books.completeSnapshot(20L);

    assertFalse(books.activeBook().containsOrder(9L));
    assertFalse(books.activeBook().containsOrder(2L));
    assertEquals(4L, books.activeBook().levelQuantity(100L, BidPutDecoder.SIDE, 100L));
    assertEquals(3L, books.activeBook().levelQuantity(100L, BidPutDecoder.SIDE, 101L));
    assertEquals(24L, books.nextExpectedSequence());
    assertEquals(2L, books.publicationVersion());
  }

  public void testGapReplayFailureAndBufferExhaustionNeverReplaceActiveBook() {
    AtomicBookSnapshot gap = books();
    gap.beginSnapshot(10L);
    gap.snapshotPut(1L, 100L, BidPutDecoder.SIDE, 1L, 10L, 1L);
    gap.completeSnapshot(10L);
    AggregatedL3Book published = gap.activeBook();
    gap.beginSnapshot(20L);
    assertThrows(
        StarbaseProtocolException.class,
        () -> gap.bufferPut(22L, 2L, 100L, BidPutDecoder.SIDE, 1L, 20L, 2L));
    assertSame(published, gap.activeBook());
    assertEquals(1L, gap.publicationVersion());

    AtomicBookSnapshot replayFailure = books();
    AggregatedL3Book empty = replayFailure.activeBook();
    replayFailure.beginSnapshot(30L);
    replayFailure.bufferReduce(31L, 404L, 100L, BidPutDecoder.SIDE, 1L);
    assertThrows(
        StarbaseProtocolException.class, () -> replayFailure.completeSnapshot(30L));
    assertSame(empty, replayFailure.activeBook());
    assertEquals(0L, replayFailure.publicationVersion());

    AtomicBookSnapshot capacity = books(1);
    AggregatedL3Book initial = capacity.activeBook();
    capacity.beginSnapshot(40L);
    capacity.bufferPut(41L, 1L, 100L, BidPutDecoder.SIDE, 1L, 10L, 1L);
    assertThrows(
        IllegalStateException.class,
        () -> capacity.bufferPut(42L, 2L, 100L, BidPutDecoder.SIDE, 1L, 11L, 2L));
    assertSame(initial, capacity.activeBook());
    assertEquals(0L, capacity.publicationVersion());
  }

  private static AtomicBookSnapshot books() {
    return books(8);
  }

  private static AtomicBookSnapshot books(int bufferCapacity) {
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
    return new AtomicBookSnapshot(8, 8, bufferCapacity, registry);
  }
}
