package io.contek.invoker.deribit.starbase.codec.orderentry;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class AmendOrderCodecsTest {

  private static volatile long sink;

  public void testRequestPinsEveryExactFieldUnitAndPadding() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);

    int encoded =
        AmendOrderRequestEncoder.encode(
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
            2,
            7L,
            6L,
            999L);

    assertEquals(88, encoded);
    assertEquals(84, TcpHeaderCodec.messageLength(frame, 0));
    AmendOrderRequestDecoder.validate(frame, 0);
    assertEquals(101L, AmendOrderRequestDecoder.clientOrderId(frame, 0));
    assertEquals(102L, AmendOrderRequestDecoder.correlationId(frame, 0));
    assertEquals(103L, AmendOrderRequestDecoder.instrumentId(frame, 0));
    assertEquals(-5_000_000_000L, AmendOrderRequestDecoder.priceMantissa(frame, 0));
    assertEquals(25L, AmendOrderRequestDecoder.quantityMantissa(frame, 0));
    assertEquals(-2, AmendOrderRequestDecoder.quantityExponent(frame, 0));
    assertTrue(AmendOrderRequestDecoder.isShowQuantityNull(frame, 0));
    assertEquals(2, AmendOrderRequestDecoder.flags(frame, 0));
    assertEquals(88, TcpHeaderCodec.validateFrame(frame, 0));
  }

  public void testSignedClientOrderIdRoundTripsButSbeNullFailsClosed() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    long clientOrderId = Long.MIN_VALUE + 1;

    AmendOrderRequestEncoder.encode(
        frame, 0, clientOrderId, 2, 3, 4, 5, -1, true, 0, 0, 1, 0, 1);
    AmendOrderRequestDecoder.validate(frame, 0);
    assertEquals(clientOrderId, AmendOrderRequestDecoder.clientOrderId(frame, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AmendOrderRequestEncoder.encode(
                frame, 0, Long.MIN_VALUE, 2, 3, 4, 5, -1, true, 0, 0, 1, 0, 1));
    frame.putLong(32, Long.MIN_VALUE);
    assertThrows(
        StarbaseProtocolException.class, () -> AmendOrderRequestDecoder.validate(frame, 0));
  }

  public void testResponsePinsFixedFieldsImmediateFillAndComboLeg() {
    ByteBuffer frame = responseFrame();

    AmendOrderResponseDecoder.validate(frame, 0);
    assertEquals(1001L, AmendOrderResponseDecoder.timestampNanos(frame, 0));
    assertEquals(1002L, AmendOrderResponseDecoder.execId(frame, 0));
    assertEquals(1003L, AmendOrderResponseDecoder.clientOrderId(frame, 0));
    assertEquals(1004L, AmendOrderResponseDecoder.correlationId(frame, 0));
    assertEquals(1005L, AmendOrderResponseDecoder.orderId(frame, 0));
    assertEquals(1006L, AmendOrderResponseDecoder.instrumentId(frame, 0));
    assertEquals(200_000_000L, AmendOrderResponseDecoder.priceMantissa(frame, 0));
    assertEquals(10L, AmendOrderResponseDecoder.quantityMantissa(frame, 0));
    assertEquals(-2, AmendOrderResponseDecoder.quantityExponent(frame, 0));
    assertEquals(3L, AmendOrderResponseDecoder.totalFilledMantissa(frame, 0));
    assertEquals(7L, AmendOrderResponseDecoder.visibleQuantityMantissa(frame, 0));
    assertEquals(1007L, AmendOrderResponseDecoder.receiveTimeNanos(frame, 0));
    assertEquals(1, AmendOrderResponseDecoder.status(frame, 0));
    assertEquals(0, AmendOrderResponseDecoder.cancelReason(frame, 0));
    assertEquals(1, AmendOrderResponseDecoder.fillCount(frame, 0));
    assertEquals(2001L, AmendOrderResponseDecoder.fillMatchId(frame, 0, 0));
    assertEquals(199_000_000L, AmendOrderResponseDecoder.fillPriceMantissa(frame, 0, 0));
    assertEquals(3L, AmendOrderResponseDecoder.fillQuantityMantissa(frame, 0, 0));
    assertEquals(-2, AmendOrderResponseDecoder.fillQuantityExponent(frame, 0, 0));
    assertEquals(1, AmendOrderResponseDecoder.legCount(frame, 0));
    assertEquals(3001L, AmendOrderResponseDecoder.legMatchId(frame, 0, 0));
    assertEquals(3002L, AmendOrderResponseDecoder.legInstrumentId(frame, 0, 0));
    assertEquals(-10_000_000L, AmendOrderResponseDecoder.legPriceMantissa(frame, 0, 0));
    assertEquals(6L, AmendOrderResponseDecoder.legQuantityMantissa(frame, 0, 0));
    assertEquals(-3, AmendOrderResponseDecoder.legQuantityExponent(frame, 0, 0));
    assertEquals(-1, AmendOrderResponseDecoder.legSide(frame, 0, 0));
    assertEquals(192, TcpHeaderCodec.validateFrame(frame, 0));
  }

  public void testRejectPinsReasonAndVariableAsciiDetails() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(frame, 0, 0, 85, 212, 11, 10L, 9L, 100L);
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

    AmendOrderRejectDecoder.validate(frame, 0);
    assertEquals(4001L, AmendOrderRejectDecoder.timestampNanos(frame, 0));
    assertEquals(4006L, AmendOrderRejectDecoder.instrumentId(frame, 0));
    assertEquals(29, AmendOrderRejectDecoder.reason(frame, 0));
    assertEquals(3, AmendOrderRejectDecoder.detailsLength(frame, 0));
    assertEquals('a', AmendOrderRejectDecoder.detailsByte(frame, 0, 1));
  }

  public void testProductionV15MmpFreezeRejectReasonIsAccepted() {
    ByteBuffer reject = ByteBuffer.allocateDirect(96).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(reject, 0, 0, 82, 212, 15, 1L, 0L, 1L);
    reject.put(32 + 48, (byte) 30);
    reject.put(32 + 49, (byte) 0);
    TcpHeaderCodec.zeroPadding(reject, 0, 82);

    AmendOrderRejectDecoder.validate(reject, 0);
    assertEquals(30, AmendOrderRejectDecoder.reason(reject, 0));
  }

  public void testInvalidRequestGroupsEnumsAndRejectDetailsFailClosed() {
    ByteBuffer request = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AmendOrderRequestEncoder.encode(
                request, 0, 1L, 2L, 3L, 4L, 5L, 0, true, 0L, 6, 1L, 0L, 1L));
    AmendOrderRequestEncoder.encode(
        request, 0, 1L, 2L, 3L, 4L, 5L, 0, true, 0L, 0, 1L, 0L, 1L);
    request.putLong(32 + 24, Long.MIN_VALUE);
    assertThrows(StarbaseProtocolException.class, () -> AmendOrderRequestDecoder.validate(request, 0));

    ByteBuffer corruptGroup = responseFrame();
    corruptGroup.putShort(32 + 93, (short) 24);
    assertThrows(
        StarbaseProtocolException.class, () -> AmendOrderResponseDecoder.validate(corruptGroup, 0));
    ByteBuffer corruptStatus = responseFrame();
    corruptStatus.put(32 + 91, (byte) 5);
    assertThrows(
        StarbaseProtocolException.class, () -> AmendOrderResponseDecoder.validate(corruptStatus, 0));

    ByteBuffer reject = ByteBuffer.allocateDirect(96).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(reject, 0, 0, 82, 212, 11, 1L, 0L, 1L);
    reject.put(32 + 48, (byte) 31);
    reject.put(32 + 49, (byte) 1);
    reject.put(32 + 50, (byte) 0);
    TcpHeaderCodec.zeroPadding(reject, 0, 82);
    assertThrows(StarbaseProtocolException.class, () -> AmendOrderRejectDecoder.validate(reject, 0));
  }

  public void testValidAmendDecodeAllocatesNothingAfterWarmup() {
    ByteBuffer request = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    AmendOrderRequestEncoder.encode(
        request, 0, 1L, 2L, 3L, 4L, 5L, 0, true, 0L, 0, 1L, 0L, 1L);
    ByteBuffer response = responseFrame();
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      AmendOrderRequestDecoder.validate(request, 0);
      AmendOrderResponseDecoder.validate(response, 0);
      sink += AmendOrderRequestDecoder.quantityMantissa(request, 0);
      sink += AmendOrderResponseDecoder.fillMatchId(response, 0, 0);
    }
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      AmendOrderRequestDecoder.validate(request, 0);
      AmendOrderResponseDecoder.validate(response, 0);
      sink += AmendOrderRequestDecoder.quantityMantissa(request, 0);
      sink += AmendOrderResponseDecoder.fillMatchId(response, 0, 0);
    }
    assertEquals(0L, bean.getThreadAllocatedBytes(threadId) - before);
  }

  private static ByteBuffer responseFrame() {
    ByteBuffer frame = ByteBuffer.allocateDirect(256).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(frame, 0, 0, 192, 210, 11, 10L, 9L, 100L);
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
    frame.put(body + 92, (byte) 0);
    int fills = body + 93;
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
    TcpHeaderCodec.zeroPadding(frame, 0, 192);
    return frame;
  }
}
