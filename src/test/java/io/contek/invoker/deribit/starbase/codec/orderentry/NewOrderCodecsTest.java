package io.contek.invoker.deribit.starbase.codec.orderentry;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.Price9Codec;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class NewOrderCodecsTest {

  private static volatile long sink;

  public void testLimitRequestPinsEveryExactFieldUnitAndPadding() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);

    int encoded =
        NewOrderRequestEncoder.encodeLimit(
            frame,
            0,
            101L,
            102L,
            103L,
            -5_000_000_000L,
            25L,
            -2,
            true,
            0L,
            104L,
            1,
            0,
            3,
            1,
            7L,
            6L,
            999L);

    assertEquals(96, encoded);
    assertEquals(95, TcpHeaderCodec.messageLength(frame, 0));
    NewOrderRequestDecoder.validate(frame, 0);
    assertEquals(101L, NewOrderRequestDecoder.clientOrderId(frame, 0));
    assertEquals(102L, NewOrderRequestDecoder.correlationId(frame, 0));
    assertEquals(103L, NewOrderRequestDecoder.instrumentId(frame, 0));
    assertEquals(-5_000_000_000L, NewOrderRequestDecoder.priceMantissa(frame, 0));
    assertEquals(25L, NewOrderRequestDecoder.quantityMantissa(frame, 0));
    assertEquals(-2, NewOrderRequestDecoder.quantityExponent(frame, 0));
    assertTrue(NewOrderRequestDecoder.isShowQuantityNull(frame, 0));
    assertEquals(104L, NewOrderRequestDecoder.selfMatchPreventionId(frame, 0));
    assertEquals(1, NewOrderRequestDecoder.side(frame, 0));
    assertEquals(0, NewOrderRequestDecoder.timeInForce(frame, 0));
    assertEquals(3, NewOrderRequestDecoder.flags(frame, 0));
    assertEquals(1, NewOrderRequestDecoder.selfTradingMode(frame, 0));
    assertEquals(96, TcpHeaderCodec.validateFrame(frame, 0));
  }

  public void testMarketRequestUsesNullPriceAndMarketLimitFlag() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);

    NewOrderRequestEncoder.encodeMarket(
        frame, 0, 201L, 202L, 203L, 5L, -1, false, 2L, 0L, -1, -2, 1, 0, 8L, 7L, 1000L);

    NewOrderRequestDecoder.validate(frame, 0);
    assertTrue(NewOrderRequestDecoder.isMarket(frame, 0));
    assertEquals(Price9Codec.NULL_MANTISSA, NewOrderRequestDecoder.priceMantissa(frame, 0));
    assertEquals(9, NewOrderRequestDecoder.flags(frame, 0));
    assertFalse(NewOrderRequestDecoder.isShowQuantityNull(frame, 0));
    assertEquals(2L, NewOrderRequestDecoder.showQuantityMantissa(frame, 0));
    assertEquals(-1, NewOrderRequestDecoder.showQuantityExponent(frame, 0));
  }

  public void testSignedClientOrderIdRoundTripsButSbeNullFailsClosed() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    long clientOrderId = Long.MIN_VALUE + 1;

    NewOrderRequestEncoder.encodeMarket(
        frame, 0, clientOrderId, 2, 3, 4, -1, true, 0, 0, 1, 0, 0, 0, 1, 0, 1);
    NewOrderRequestDecoder.validate(frame, 0);
    assertEquals(clientOrderId, NewOrderRequestDecoder.clientOrderId(frame, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            NewOrderRequestEncoder.encodeMarket(
                frame, 0, Long.MIN_VALUE, 2, 3, 4, -1, true, 0, 0, 1, 0, 0, 0, 1, 0, 1));
    frame.putLong(32, Long.MIN_VALUE);
    assertThrows(
        StarbaseProtocolException.class, () -> NewOrderRequestDecoder.validate(frame, 0));
  }

  public void testResponsePinsFixedFieldsImmediateFillAndComboLeg() {
    ByteBuffer frame = responseFrame();

    NewOrderResponseDecoder.validate(frame, 0);
    assertEquals(1001L, NewOrderResponseDecoder.timestampNanos(frame, 0));
    assertEquals(1002L, NewOrderResponseDecoder.execId(frame, 0));
    assertEquals(1003L, NewOrderResponseDecoder.clientOrderId(frame, 0));
    assertEquals(1004L, NewOrderResponseDecoder.correlationId(frame, 0));
    assertEquals(1005L, NewOrderResponseDecoder.orderId(frame, 0));
    assertEquals(1006L, NewOrderResponseDecoder.instrumentId(frame, 0));
    assertEquals(200_000_000L, NewOrderResponseDecoder.priceMantissa(frame, 0));
    assertEquals(10L, NewOrderResponseDecoder.quantityMantissa(frame, 0));
    assertEquals(-2, NewOrderResponseDecoder.quantityExponent(frame, 0));
    assertEquals(3L, NewOrderResponseDecoder.totalFilledMantissa(frame, 0));
    assertEquals(7L, NewOrderResponseDecoder.visibleQuantityMantissa(frame, 0));
    assertEquals(1007L, NewOrderResponseDecoder.receiveTimeNanos(frame, 0));
    assertEquals(1, NewOrderResponseDecoder.side(frame, 0));
    assertEquals(1, NewOrderResponseDecoder.status(frame, 0));
    assertEquals(0, NewOrderResponseDecoder.cancelReason(frame, 0));
    assertEquals(1, NewOrderResponseDecoder.fillCount(frame, 0));
    assertEquals(2001L, NewOrderResponseDecoder.fillMatchId(frame, 0, 0));
    assertEquals(199_000_000L, NewOrderResponseDecoder.fillPriceMantissa(frame, 0, 0));
    assertEquals(3L, NewOrderResponseDecoder.fillQuantityMantissa(frame, 0, 0));
    assertEquals(-2, NewOrderResponseDecoder.fillQuantityExponent(frame, 0, 0));
    assertEquals(1, NewOrderResponseDecoder.legCount(frame, 0));
    assertEquals(3001L, NewOrderResponseDecoder.legMatchId(frame, 0, 0));
    assertEquals(3002L, NewOrderResponseDecoder.legInstrumentId(frame, 0, 0));
    assertEquals(-10_000_000L, NewOrderResponseDecoder.legPriceMantissa(frame, 0, 0));
    assertEquals(6L, NewOrderResponseDecoder.legQuantityMantissa(frame, 0, 0));
    assertEquals(-3, NewOrderResponseDecoder.legQuantityExponent(frame, 0, 0));
    assertEquals(-1, NewOrderResponseDecoder.legSide(frame, 0, 0));
  }

  public void testRejectPinsReasonAndVariableAsciiDetails() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(frame, 0, 0, 85, 202, 11, 10L, 9L, 100L);
    int body = 32;
    for (int index = 0; index < 6; index++) {
      frame.putLong(body + index * 8, 4001L + index);
    }
    frame.put(body + 48, (byte) 29);
    frame.put(body + 49, (byte) 3);
    frame.put(body + 50, (byte) 'b');
    frame.put(body + 51, (byte) 'a');
    frame.put(body + 52, (byte) 'd');
    TcpHeaderCodec.zeroPadding(frame, 0, 85);

    NewOrderRejectDecoder.validate(frame, 0);
    assertEquals(4001L, NewOrderRejectDecoder.timestampNanos(frame, 0));
    assertEquals(4006L, NewOrderRejectDecoder.instrumentId(frame, 0));
    assertEquals(29, NewOrderRejectDecoder.reason(frame, 0));
    assertEquals(3, NewOrderRejectDecoder.detailsLength(frame, 0));
    assertEquals('a', NewOrderRejectDecoder.detailsByte(frame, 0, 1));
    assertEquals(88, TcpHeaderCodec.validateFrame(frame, 0));
  }

  public void testContradictoryRequestsAndCorruptResponseGroupsAndRejectsFailClosed() {
    ByteBuffer request = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            NewOrderRequestEncoder.encodeLimit(
                request, 0, 1L, 2L, 3L, 4L, 5L, 0, true, 0L, 0L, 1, 0,
                NewOrderRequestEncoder.MARKET_LIMIT, 0, 1L, 0L, 1L));
    NewOrderRequestEncoder.encodeMarket(
        request, 0, 1L, 2L, 3L, 5L, 0, true, 0L, 0L, 1, 0, 0, 0, 1L, 0L, 1L);
    request.putLong(32 + 24, 4L);
    assertThrows(StarbaseProtocolException.class, () -> NewOrderRequestDecoder.validate(request, 0));

    ByteBuffer corruptGroup = responseFrame();
    corruptGroup.putShort(32 + 94, (short) 24);
    assertThrows(
        StarbaseProtocolException.class, () -> NewOrderResponseDecoder.validate(corruptGroup, 0));
    ByteBuffer corruptStatus = responseFrame();
    corruptStatus.put(32 + 92, (byte) 5);
    assertThrows(
        StarbaseProtocolException.class, () -> NewOrderResponseDecoder.validate(corruptStatus, 0));

    ByteBuffer reject = ByteBuffer.allocateDirect(96).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(reject, 0, 0, 82, 202, 11, 1L, 0L, 1L);
    reject.put(32 + 48, (byte) 30);
    reject.put(32 + 49, (byte) 1);
    reject.put(32 + 50, (byte) 0);
    TcpHeaderCodec.zeroPadding(reject, 0, 82);
    assertThrows(StarbaseProtocolException.class, () -> NewOrderRejectDecoder.validate(reject, 0));
  }

  public void testValidNewOrderDecodeAllocatesNothingAfterWarmup() {
    ByteBuffer request = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    NewOrderRequestEncoder.encodeMarket(
        request, 0, 1L, 2L, 3L, 5L, 0, true, 0L, 0L, 1, 0, 0, 0, 1L, 0L, 1L);
    ByteBuffer response = responseFrame();
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      NewOrderRequestDecoder.validate(request, 0);
      NewOrderResponseDecoder.validate(response, 0);
      sink += NewOrderRequestDecoder.quantityMantissa(request, 0);
      sink += NewOrderResponseDecoder.fillMatchId(response, 0, 0);
    }
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      NewOrderRequestDecoder.validate(request, 0);
      NewOrderResponseDecoder.validate(response, 0);
      sink += NewOrderRequestDecoder.quantityMantissa(request, 0);
      sink += NewOrderResponseDecoder.fillMatchId(response, 0, 0);
    }
    assertEquals(0L, bean.getThreadAllocatedBytes(threadId) - before);
  }

  private static ByteBuffer responseFrame() {
    ByteBuffer frame = ByteBuffer.allocateDirect(256).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(frame, 0, 0, 193, 200, 11, 10L, 9L, 100L);
    int body = 32;
    for (int index = 0; index < 6; index++) {
      frame.putLong(body + index * 8, 1001L + index);
    }
    frame.putLong(body + 48, 200_000_000L);
    Decimal72Codec.put(frame, body + 56, 10L, -2);
    Decimal72Codec.put(frame, body + 65, 3L, -2);
    Decimal72Codec.put(frame, body + 74, 7L, -2);
    frame.putLong(body + 83, 1007L);
    frame.put(body + 91, (byte) 1);
    frame.put(body + 92, (byte) 1);
    frame.put(body + 93, (byte) 0);
    int fills = body + 94;
    frame.putShort(fills, (short) 25);
    frame.putShort(fills + 2, (short) 1);
    int fill = fills + 4;
    frame.putLong(fill, 2001L);
    frame.putLong(fill + 8, 199_000_000L);
    Decimal72Codec.put(frame, fill + 16, 3L, -2);
    int legs = fill + 25;
    frame.putShort(legs, (short) 34);
    frame.putShort(legs + 2, (short) 1);
    int leg = legs + 4;
    frame.putLong(leg, 3001L);
    frame.putLong(leg + 8, 3002L);
    frame.putLong(leg + 16, -10_000_000L);
    Decimal72Codec.put(frame, leg + 24, 6L, -3);
    frame.put(leg + 33, (byte) -1);
    TcpHeaderCodec.zeroPadding(frame, 0, 193);
    return frame;
  }
}
