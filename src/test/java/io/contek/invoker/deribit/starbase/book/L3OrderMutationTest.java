package io.contek.invoker.deribit.starbase.book;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.codec.marketdata.AskPutDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidPutDecoder;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class L3OrderMutationTest {

  public void testPartialAndFullReductionUseAuthoritativeRemainingQuantity() {
    L3OrderStore store = store(2);
    store.put(10L, 100L, BidPutDecoder.SIDE, 1_000L, 20L, 30L);

    assertEquals(
        L3OrderStore.REDUCED,
        store.reduce(10L, 100L, BidPutDecoder.SIDE, 600L));
    assertEquals(600L, store.quantityMantissa(10L));
    assertEquals(20L, store.priceMantissa(10L));
    assertEquals(30L, store.sortOrderId(10L));
    assertEquals(
        L3OrderStore.DUPLICATE,
        store.reduce(10L, 100L, BidPutDecoder.SIDE, 600L));
    assertEquals(
        L3OrderStore.REMOVED,
        store.reduce(10L, 100L, BidPutDecoder.SIDE, 0L));
    assertFalse(store.contains(10L));
    assertEquals(0, store.size());
  }

  public void testExplicitDeleteChecksExactInstrumentAndSide() {
    L3OrderStore store = store(2);
    store.put(20L, 100L, AskPutDecoder.SIDE, 50L, 60L, 70L);

    assertThrows(
        StarbaseProtocolException.class,
        () -> store.delete(20L, 100L, BidPutDecoder.SIDE));
    assertThrows(
        StarbaseProtocolException.class,
        () -> store.delete(20L, 101L, AskPutDecoder.SIDE));
    assertEquals(
        L3OrderStore.REMOVED,
        store.delete(20L, 100L, AskPutDecoder.SIDE));
    assertThrows(
        StarbaseProtocolException.class,
        () -> store.delete(20L, 100L, AskPutDecoder.SIDE));
  }

  public void testInvalidIncreaseNegativeAndMissingOrderFailClosed() {
    L3OrderStore store = store(2);
    store.put(30L, 100L, BidPutDecoder.SIDE, 100L, 1L, 2L);

    assertThrows(
        StarbaseProtocolException.class,
        () -> store.reduce(30L, 100L, BidPutDecoder.SIDE, 101L));
    assertThrows(
        IllegalArgumentException.class,
        () -> store.reduce(30L, 100L, BidPutDecoder.SIDE, -1L));
    assertThrows(
        StarbaseProtocolException.class,
        () -> store.reduce(999L, 100L, BidPutDecoder.SIDE, 1L));
    assertEquals(100L, store.quantityMantissa(30L));
  }

  public void testDeletedSlotsAreReusableWithoutBreakingProbeChains() {
    L3OrderStore store = store(2);
    store.put(1L, 100L, BidPutDecoder.SIDE, 10L, 1L, 1L);
    store.put(17L, 100L, AskPutDecoder.SIDE, 20L, 2L, 2L);

    store.delete(1L, 100L, BidPutDecoder.SIDE);
    assertTrue(store.contains(17L));
    store.put(33L, 100L, BidPutDecoder.SIDE, 30L, 3L, 3L);

    assertTrue(store.contains(17L));
    assertTrue(store.contains(33L));
    assertEquals(2, store.size());
  }

  public void testAppliesAllFourValidatedMutationTemplates() {
    L3OrderStore store = store(4);
    store.put(41L, 100L, BidPutDecoder.SIDE, 10L, 1L, 1L);
    store.put(42L, 100L, AskPutDecoder.SIDE, 20L, 2L, 2L);
    store.put(43L, 100L, BidPutDecoder.SIDE, 30L, 3L, 3L);
    store.put(44L, 100L, AskPutDecoder.SIDE, 40L, 4L, 4L);

    assertEquals(
        L3OrderStore.REDUCED,
        store.applyBidQtyReduced(reducedMessage(22, 41L, 100L, 5L), 0));
    assertEquals(
        L3OrderStore.REDUCED,
        store.applyAskQtyReduced(reducedMessage(23, 42L, 100L, 10L), 0));
    assertEquals(
        L3OrderStore.REMOVED,
        store.applyBidDelete(deleteMessage(24, 43L, 100L), 0));
    assertEquals(
        L3OrderStore.REMOVED,
        store.applyAskDelete(deleteMessage(25, 44L, 100L), 0));
  }

  private static L3OrderStore store(int capacity) {
    InstrumentRegistry registry = new InstrumentRegistry(2);
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
    registry.upsert(
        101L,
        "ETH-PERPETUAL",
        ProductGroup.ETH,
        "ETH",
        "USD",
        -8,
        1L,
        1L,
        0,
        0,
        1);
    return new L3OrderStore(capacity, registry);
  }

  private static ByteBuffer reducedMessage(
      int templateId, long orderId, long instrumentId, long remainingQuantity) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(40).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(0, (short) 40);
    buffer.putShort(2, (short) templateId);
    buffer.putShort(4, (short) 1);
    buffer.putLong(16, orderId);
    buffer.putLong(24, instrumentId);
    buffer.putLong(32, remainingQuantity);
    return buffer;
  }

  private static ByteBuffer deleteMessage(
      int templateId, long orderId, long instrumentId) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(32).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(0, (short) 32);
    buffer.putShort(2, (short) templateId);
    buffer.putShort(4, (short) 1);
    buffer.putLong(16, orderId);
    buffer.putLong(24, instrumentId);
    return buffer;
  }
}
