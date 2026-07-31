package io.contek.invoker.deribit.starbase.codec.orderentry;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class OrderLifecycleCodecsTest {

  private static volatile long sink;

  public void testSubsequentFillPinsEveryExactFieldAndGroupBoundary() {
    ByteBuffer frame = ByteBuffer.allocateDirect(160).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(frame, 0, 0, 116, 300, 11, 20L, 19L, 100L);
    int body = 32;
    frame.putLong(body, 1001L);
    frame.putLong(body + 8, 1002L);
    int fills = body + 16;
    frame.putShort(fills, (short) 60);
    frame.putShort(fills + 2, (short) 1);
    int fill = fills + 4;
    frame.putLong(fill, 2001L);
    frame.putLong(fill + 8, 2002L);
    frame.putLong(fill + 16, 2003L);
    frame.putLong(fill + 24, 2004L);
    frame.putLong(fill + 32, 199_000_000L);
    Decimal72Codec.put(frame, fill + 40, 3L, -2);
    Decimal72Codec.put(frame, fill + 49, 7L, -2);
    frame.put(fill + 58, (byte) -1);
    frame.put(fill + 59, (byte) 3);
    int legs = fill + 60;
    frame.putShort(legs, (short) 34);
    frame.putShort(legs + 2, (short) 0);
    TcpHeaderCodec.zeroPadding(frame, 0, 116);

    OrderFilledDecoder.validate(frame, 0);
    assertEquals(1001L, OrderFilledDecoder.timestampNanos(frame, 0));
    assertEquals(1002L, OrderFilledDecoder.execId(frame, 0));
    assertEquals(1, OrderFilledDecoder.fillCount(frame, 0));
    assertEquals(2001L, OrderFilledDecoder.clientOrderId(frame, 0, 0));
    assertEquals(2002L, OrderFilledDecoder.orderId(frame, 0, 0));
    assertEquals(2003L, OrderFilledDecoder.instrumentId(frame, 0, 0));
    assertEquals(2004L, OrderFilledDecoder.matchId(frame, 0, 0));
    assertEquals(199_000_000L, OrderFilledDecoder.priceMantissa(frame, 0, 0));
    assertEquals(3L, OrderFilledDecoder.fillQuantityMantissa(frame, 0, 0));
    assertEquals(-2, OrderFilledDecoder.fillQuantityExponent(frame, 0, 0));
    assertEquals(7L, OrderFilledDecoder.totalFilledMantissa(frame, 0, 0));
    assertEquals(-1, OrderFilledDecoder.side(frame, 0, 0));
    assertEquals(3, OrderFilledDecoder.flags(frame, 0, 0));
    assertEquals(0, OrderFilledDecoder.legCount(frame, 0));
    assertEquals(120, TcpHeaderCodec.validateFrame(frame, 0));
  }

  public void testMultipartCancellationPinsOrderStateReasonAndQuoteFlag() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(frame, 0, 0, 88, 310, 11, 21L, 20L, 101L);
    int body = 32;
    frame.putLong(body, 3001L);
    frame.putLong(body + 8, 3002L);
    frame.put(body + 16, (byte) 1);
    int orders = body + 17;
    frame.putShort(orders, (short) 35);
    frame.putShort(orders + 2, (short) 1);
    int order = orders + 4;
    frame.putLong(order, 4001L);
    frame.putLong(order + 8, 4002L);
    frame.putLong(order + 16, 4003L);
    Decimal72Codec.put(frame, order + 24, 5L, -2);
    frame.put(order + 33, (byte) 17);
    frame.put(order + 34, (byte) 1);

    OrdersCanceledDecoder.validate(frame, 0);
    assertEquals(3001L, OrdersCanceledDecoder.timestampNanos(frame, 0));
    assertEquals(3002L, OrdersCanceledDecoder.execId(frame, 0));
    assertEquals(1, OrdersCanceledDecoder.multiPartFlags(frame, 0));
    assertEquals(1, OrdersCanceledDecoder.orderCount(frame, 0));
    assertEquals(4001L, OrdersCanceledDecoder.clientOrderId(frame, 0, 0));
    assertEquals(4002L, OrdersCanceledDecoder.orderId(frame, 0, 0));
    assertEquals(4003L, OrdersCanceledDecoder.instrumentId(frame, 0, 0));
    assertEquals(5L, OrdersCanceledDecoder.totalFilledMantissa(frame, 0, 0));
    assertEquals(-2, OrdersCanceledDecoder.totalFilledExponent(frame, 0, 0));
    assertEquals(17, OrdersCanceledDecoder.cancelReason(frame, 0, 0));
    assertEquals(1, OrdersCanceledDecoder.flags(frame, 0, 0));
  }

  public void testSpeedBumpedOrderPlacedPinsCorrelationStateAndEmptyGroups() {
    ByteBuffer frame = orderPlacedFrame();

    OrderPlacedDecoder.validate(frame, 0);
    assertEquals(5001L, OrderPlacedDecoder.timestampNanos(frame, 0));
    assertEquals(5003L, OrderPlacedDecoder.clientOrderId(frame, 0));
    assertEquals(5004L, OrderPlacedDecoder.orderId(frame, 0));
    assertEquals(5005L, OrderPlacedDecoder.instrumentId(frame, 0));
    assertEquals(210_000_000L, OrderPlacedDecoder.priceMantissa(frame, 0));
    assertEquals(10L, OrderPlacedDecoder.quantityMantissa(frame, 0));
    assertEquals(2L, OrderPlacedDecoder.totalFilledMantissa(frame, 0));
    assertEquals(8L, OrderPlacedDecoder.visibleQuantityMantissa(frame, 0));
    assertEquals(1, OrderPlacedDecoder.status(frame, 0));
    assertEquals(0, OrderPlacedDecoder.cancelReason(frame, 0));
    assertEquals(5006L, OrderPlacedDecoder.correlationId(frame, 0));
    assertEquals(0, OrderPlacedDecoder.fillCount(frame, 0));
    assertEquals(0, OrderPlacedDecoder.legCount(frame, 0));
  }

  public void testCorruptGroupFlagsPaddingEnumsAndQuantitiesFailClosed() {
    ByteBuffer filled = orderFilledFrame();
    filled.putShort(32 + 16, (short) 59);
    assertThrows(StarbaseProtocolException.class, () -> OrderFilledDecoder.validate(filled, 0));

    ByteBuffer canceled = canceledFrame();
    canceled.put(32 + 16, (byte) 2);
    assertThrows(
        StarbaseProtocolException.class, () -> OrdersCanceledDecoder.validate(canceled, 0));

    ByteBuffer placed = orderPlacedFrame();
    placed.put(32 + 77, (byte) 1);
    assertThrows(StarbaseProtocolException.class, () -> OrderPlacedDecoder.validate(placed, 0));
  }

  public void testValidLifecycleDecodeAllocatesNothingAfterWarmup() {
    ByteBuffer filled = orderFilledFrame();
    ByteBuffer canceled = canceledFrame();
    ByteBuffer placed = orderPlacedFrame();
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      OrderFilledDecoder.validate(filled, 0);
      OrdersCanceledDecoder.validate(canceled, 0);
      OrderPlacedDecoder.validate(placed, 0);
      sink += OrderFilledDecoder.matchId(filled, 0, 0);
      sink += OrdersCanceledDecoder.orderId(canceled, 0, 0);
      sink += OrderPlacedDecoder.orderId(placed, 0);
    }
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      OrderFilledDecoder.validate(filled, 0);
      OrdersCanceledDecoder.validate(canceled, 0);
      OrderPlacedDecoder.validate(placed, 0);
      sink += OrderFilledDecoder.matchId(filled, 0, 0);
      sink += OrdersCanceledDecoder.orderId(canceled, 0, 0);
      sink += OrderPlacedDecoder.orderId(placed, 0);
    }
    assertEquals(0L, bean.getThreadAllocatedBytes(threadId) - before);
  }

  private static ByteBuffer orderFilledFrame() {
    ByteBuffer frame = ByteBuffer.allocateDirect(160).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(frame, 0, 0, 116, 300, 11, 20L, 19L, 100L);
    frame.putLong(32, 1001L);
    frame.putLong(40, 1002L);
    frame.putShort(48, (short) 60);
    frame.putShort(50, (short) 1);
    int fill = 52;
    frame.putLong(fill, 2001L);
    frame.putLong(fill + 8, 2002L);
    frame.putLong(fill + 16, 2003L);
    frame.putLong(fill + 24, 2004L);
    frame.putLong(fill + 32, 199_000_000L);
    Decimal72Codec.put(frame, fill + 40, 3L, -2);
    Decimal72Codec.put(frame, fill + 49, 7L, -2);
    frame.put(fill + 58, (byte) -1);
    frame.put(fill + 59, (byte) 3);
    frame.putShort(112, (short) 34);
    frame.putShort(114, (short) 0);
    TcpHeaderCodec.zeroPadding(frame, 0, 116);
    return frame;
  }

  private static ByteBuffer canceledFrame() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(frame, 0, 0, 88, 310, 11, 21L, 20L, 101L);
    frame.putLong(32, 3001L);
    frame.putLong(40, 3002L);
    frame.put(48, (byte) 1);
    frame.putShort(49, (short) 35);
    frame.putShort(51, (short) 1);
    int order = 53;
    frame.putLong(order, 4001L);
    frame.putLong(order + 8, 4002L);
    frame.putLong(order + 16, 4003L);
    Decimal72Codec.put(frame, order + 24, 5L, -2);
    frame.put(order + 33, (byte) 17);
    frame.put(order + 34, (byte) 1);
    return frame;
  }

  private static ByteBuffer orderPlacedFrame() {
    ByteBuffer frame = ByteBuffer.allocateDirect(160).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(frame, 0, 0, 128, 312, 11, 22L, 21L, 102L);
    int body = 32;
    frame.putLong(body, 5001L);
    frame.putLong(body + 8, 5002L);
    frame.putLong(body + 16, 5003L);
    frame.putLong(body + 24, 5004L);
    frame.putLong(body + 32, 5005L);
    frame.putLong(body + 40, 210_000_000L);
    Decimal72Codec.put(frame, body + 48, 10L, -2);
    Decimal72Codec.put(frame, body + 57, 2L, -2);
    Decimal72Codec.put(frame, body + 66, 8L, -2);
    frame.put(body + 75, (byte) 1);
    frame.put(body + 76, (byte) 0);
    frame.put(body + 77, (byte) 0);
    frame.put(body + 78, (byte) 0);
    frame.put(body + 79, (byte) 0);
    frame.putLong(body + 80, 5006L);
    frame.putShort(body + 88, (short) 25);
    frame.putShort(body + 90, (short) 0);
    frame.putShort(body + 92, (short) 34);
    frame.putShort(body + 94, (short) 0);
    return frame;
  }
}
