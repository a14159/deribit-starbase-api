package io.contek.invoker.deribit.starbase.book;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.marketdata.AskPutDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidPutDecoder;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class L3OrderStoreTest {

  private static volatile long sink;

  public void testInsertsBidAndAskWithExactLongIdentityAndPriority() {
    InstrumentRegistry registry = registry();
    L3OrderStore store = new L3OrderStore(4, registry);

    assertEquals(
        L3OrderStore.INSERTED,
        store.put(
            6_000_000_001L,
            5_000_000_123L,
            BidPutDecoder.SIDE,
            123_456L,
            99_500_000_000L,
            7_000_000_001L));
    assertEquals(
        L3OrderStore.INSERTED,
        store.put(
            6_000_000_002L,
            5_000_000_123L,
            AskPutDecoder.SIDE,
            654_321L,
            99_600_000_000L,
            7_000_000_002L));

    assertEquals(2, store.size());
    assertEquals(5_000_000_123L, store.instrumentId(6_000_000_001L));
    assertEquals(BidPutDecoder.SIDE, store.side(6_000_000_001L));
    assertEquals(123_456L, store.quantityMantissa(6_000_000_001L));
    assertEquals(99_500_000_000L, store.priceMantissa(6_000_000_001L));
    assertEquals(7_000_000_001L, store.sortOrderId(6_000_000_001L));
    assertEquals(AskPutDecoder.SIDE, store.side(6_000_000_002L));
  }

  public void testExactDuplicateIsNoOpAndAmendReplacesMutableFields() {
    L3OrderStore store = new L3OrderStore(2, registry());
    store.put(10L, 5_000_000_123L, BidPutDecoder.SIDE, 100L, 200L, 300L);

    assertEquals(
        L3OrderStore.DUPLICATE,
        store.put(10L, 5_000_000_123L, BidPutDecoder.SIDE, 100L, 200L, 300L));
    assertEquals(
        L3OrderStore.UPDATED,
        store.put(10L, 5_000_000_123L, BidPutDecoder.SIDE, 90L, 210L, 301L));

    assertEquals(1, store.size());
    assertEquals(90L, store.quantityMantissa(10L));
    assertEquals(210L, store.priceMantissa(10L));
    assertEquals(301L, store.sortOrderId(10L));
  }

  public void testIdentityMutationUnknownInstrumentAndCapacityFailClosed() {
    L3OrderStore store = new L3OrderStore(1, registry());
    store.put(10L, 5_000_000_123L, BidPutDecoder.SIDE, 1L, 2L, 3L);

    assertThrows(
        StarbaseProtocolException.class,
        () -> store.put(10L, 5_000_000_123L, AskPutDecoder.SIDE, 1L, 2L, 3L));
    assertThrows(
        StarbaseProtocolException.class,
        () -> store.put(10L, 999L, BidPutDecoder.SIDE, 1L, 2L, 3L));
    assertThrows(
        StarbaseProtocolException.class,
        () -> store.put(11L, 999L, BidPutDecoder.SIDE, 1L, 2L, 3L));
    assertThrows(
        IllegalStateException.class,
        () -> store.put(11L, 5_000_000_123L, BidPutDecoder.SIDE, 1L, 2L, 3L));
    assertThrows(StarbaseProtocolException.class, () -> store.side(999L));
  }

  public void testAppliesValidatedBidAndAskPutWireMessages() {
    L3OrderStore store = new L3OrderStore(2, registry());

    assertEquals(
        L3OrderStore.INSERTED,
        store.applyBidPut(
            putMessage(20, 51L, 5_000_000_123L, 5L, 6L, 7L), 0));
    assertEquals(
        L3OrderStore.INSERTED,
        store.applyAskPut(
            putMessage(21, 52L, 5_000_000_123L, 8L, 9L, 10L), 0));

    assertEquals(BidPutDecoder.SIDE, store.side(51L));
    assertEquals(AskPutDecoder.SIDE, store.side(52L));
  }

  public void testAmendHotPathAllocatesNothingAfterWarmup() {
    L3OrderStore store = new L3OrderStore(2, registry());
    store.put(10L, 5_000_000_123L, BidPutDecoder.SIDE, 1L, 2L, 3L);
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      sink +=
          store.put(
              10L,
              5_000_000_123L,
              BidPutDecoder.SIDE,
              iteration + 1L,
              iteration + 2L,
              iteration + 3L);
    }
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      sink +=
          store.put(
              10L,
              5_000_000_123L,
              BidPutDecoder.SIDE,
              iteration + 2L,
              iteration + 3L,
              iteration + 4L);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated);
  }

  private static InstrumentRegistry registry() {
    InstrumentRegistry registry = new InstrumentRegistry(2);
    registry.upsert(
        5_000_000_123L,
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

  private static ByteBuffer putMessage(
      int templateId,
      long orderId,
      long instrumentId,
      long quantity,
      long price,
      long sortOrderId) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(56).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(0, (short) 56);
    buffer.putShort(2, (short) templateId);
    buffer.putShort(4, (short) 1);
    buffer.putLong(16, orderId);
    buffer.putLong(24, instrumentId);
    buffer.putLong(32, quantity);
    buffer.putLong(40, price);
    buffer.putLong(48, sortOrderId);
    return buffer;
  }
}
