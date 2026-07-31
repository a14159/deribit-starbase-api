package io.contek.invoker.deribit.starbase.codec.common;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class TemplateDispatchTest {

  private static volatile int sink;

  public void testEveryPinnedOrderEntryTemplateIsKnownExceptSchemaGeneratorDummy() {
    int[] knownIds = {
      1, 2, 4, 5, 10, 11, 20, 21, 30,
      100, 110, 120, 125, 130, 140, 145, 155, 156,
      200, 202, 210, 212, 220, 222, 230, 232, 240, 242,
      280, 281, 282, 283, 300, 310, 312, 314, 320, 322, 324, 326
    };
    for (int templateId : knownIds) {
      assertEquals(templateId, OrderEntryTemplateDispatch.requireKnown(templateId));
    }

    assertThrows(
        StarbaseProtocolException.class, () -> OrderEntryTemplateDispatch.requireKnown(9999));
    assertThrows(
        StarbaseProtocolException.class, () -> OrderEntryTemplateDispatch.requireKnown(65_535));
  }

  public void testEveryPinnedMarketDataTemplateIsKnownExceptSchemaGeneratorDummy() {
    int[] knownIds = {10, 11, 14, 15, 16, 20, 21, 22, 23, 24, 25, 30, 31, 33, 100, 101, 119, 200, 202};
    for (int templateId : knownIds) {
      assertEquals(templateId, MarketDataTemplateDispatch.requireKnown(templateId));
    }

    assertThrows(
        StarbaseProtocolException.class, () -> MarketDataTemplateDispatch.requireKnown(1));
    assertThrows(
        StarbaseProtocolException.class, () -> MarketDataTemplateDispatch.requireKnown(999));
  }

  public void testCompleteFramesRequirePinnedVersionKnownTemplateAndValidBounds() {
    ByteBuffer order = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(order, 0, 0, 32, 100, 11, 1, 0, 2);
    assertEquals(100, OrderEntryTemplateDispatch.validateFrame(order, 0));

    order.putShort(TcpHeaderCodec.VERSION_OFFSET, (short) 10);
    assertThrows(
        StarbaseProtocolException.class,
        () -> OrderEntryTemplateDispatch.validateFrame(order, 0));
    order.putShort(TcpHeaderCodec.VERSION_OFFSET, (short) 11);
    order.putShort(TcpHeaderCodec.MESSAGE_TYPE_ID_OFFSET, (short) 9999);
    assertThrows(
        StarbaseProtocolException.class,
        () -> OrderEntryTemplateDispatch.validateFrame(order, 0));

    ByteBuffer market = marketMessage(20, 1, 16);
    assertEquals(20, MarketDataTemplateDispatch.validateMessage(market, 0));
    market.putShort(MarketDataMessageHeaderCodec.VERSION_OFFSET, (short) 0);
    assertEquals(20, MarketDataTemplateDispatch.validateMessage(market, 0));
    market.putShort(MarketDataMessageHeaderCodec.VERSION_OFFSET, (short) 2);
    assertThrows(
        StarbaseProtocolException.class,
        () -> MarketDataTemplateDispatch.validateMessage(market, 0));
    market.putShort(MarketDataMessageHeaderCodec.VERSION_OFFSET, (short) 1);
    market.putShort(MarketDataMessageHeaderCodec.MESSAGE_LENGTH_OFFSET, (short) 17);
    assertThrows(
        StarbaseProtocolException.class,
        () -> MarketDataTemplateDispatch.validateMessage(market, 0));
  }

  public void testStateChangingClassificationIsExplicitAndConservative() {
    assertFalse(OrderEntryTemplateDispatch.isStateChanging(10));
    assertTrue(OrderEntryTemplateDispatch.isStateChanging(100));
    assertTrue(OrderEntryTemplateDispatch.isStateChanging(300));
    assertTrue(MarketDataTemplateDispatch.isStateChanging(10));
    assertTrue(MarketDataTemplateDispatch.isStateChanging(20));
    assertTrue(MarketDataTemplateDispatch.isStateChanging(31));
    assertFalse(MarketDataTemplateDispatch.isStateChanging(119));
    assertThrows(
        StarbaseProtocolException.class, () -> MarketDataTemplateDispatch.isStateChanging(999));
  }

  public void testValidDispatchHotPathAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ByteBuffer order = ByteBuffer.allocateDirect(32).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(order, 0, 0, 32, 100, 11, 1, 0, 2);
    ByteBuffer market = marketMessage(20, 1, 16);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(order, market);
    }

    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(order, market);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated, "valid template dispatch hot path allocated bytes");
  }

  private static ByteBuffer marketMessage(int templateId, int version, int length) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(MarketDataMessageHeaderCodec.MESSAGE_LENGTH_OFFSET, (short) length);
    buffer.putShort(MarketDataMessageHeaderCodec.TEMPLATE_ID_OFFSET, (short) templateId);
    buffer.putShort(MarketDataMessageHeaderCodec.VERSION_OFFSET, (short) version);
    return buffer;
  }

  private static void exercise(ByteBuffer order, ByteBuffer market) {
    sink += OrderEntryTemplateDispatch.validateFrame(order, 0);
    sink += MarketDataTemplateDispatch.validateMessage(market, 0);
  }
}
