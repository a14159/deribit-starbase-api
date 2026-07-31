package io.contek.invoker.deribit.starbase.codec.orderentry;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class CancelOrderCodecsTest {

  private static volatile long sink;

  public void testClientOrderIdRequestPinsEveryExactFieldAndPadding() {
    ByteBuffer frame = ByteBuffer.allocateDirect(96).order(ByteOrder.LITTLE_ENDIAN);

    int encoded =
        CancelOrderRequestEncoder.encode(
            frame, 0, 101L, 102L, 103L, 7L, 6L, 999L);

    assertEquals(56, encoded);
    assertEquals(56, TcpHeaderCodec.messageLength(frame, 0));
    CancelOrderRequestDecoder.validate(frame, 0);
    assertEquals(101L, CancelOrderRequestDecoder.clientOrderId(frame, 0));
    assertEquals(102L, CancelOrderRequestDecoder.correlationId(frame, 0));
    assertEquals(103L, CancelOrderRequestDecoder.instrumentId(frame, 0));
    assertEquals(56, TcpHeaderCodec.validateFrame(frame, 0));
  }

  public void testExchangeOrderIdRequestPinsEveryExactField() {
    ByteBuffer frame = ByteBuffer.allocateDirect(96).order(ByteOrder.LITTLE_ENDIAN);

    int encoded =
        CancelOrderByIdRequestEncoder.encode(
            frame, 0, Long.MAX_VALUE - 1, 202L, 203L, 8L, 7L, 1000L);

    assertEquals(56, encoded);
    CancelOrderByIdRequestDecoder.validate(frame, 0);
    assertEquals(Long.MAX_VALUE - 1, CancelOrderByIdRequestDecoder.orderId(frame, 0));
    assertEquals(202L, CancelOrderByIdRequestDecoder.correlationId(frame, 0));
    assertEquals(203L, CancelOrderByIdRequestDecoder.instrumentId(frame, 0));
  }

  public void testMassCancelRequestPinsNullableScopeProductSideAndPadding() {
    ByteBuffer frame = ByteBuffer.allocateDirect(96).order(ByteOrder.LITTLE_ENDIAN);

    int encoded =
        MassCancelRequestEncoder.encode(
            frame,
            0,
            301L,
            Long.MIN_VALUE,
            303L,
            2,
            0,
            9L,
            8L,
            1001L);

    assertEquals(64, encoded);
    assertEquals(58, TcpHeaderCodec.messageLength(frame, 0));
    MassCancelRequestDecoder.validate(frame, 0);
    assertEquals(301L, MassCancelRequestDecoder.correlationId(frame, 0));
    assertTrue(MassCancelRequestDecoder.isCurrencyPairIdNull(frame, 0));
    assertEquals(303L, MassCancelRequestDecoder.instrumentId(frame, 0));
    assertEquals(2, MassCancelRequestDecoder.productType(frame, 0));
    assertEquals(0, MassCancelRequestDecoder.side(frame, 0));
    assertEquals(64, TcpHeaderCodec.validateFrame(frame, 0));
  }

  public void testCancelResponseAndRejectPinAllFieldsAndOptionalOrderId() {
    ByteBuffer response = fixedFrame(220, 88, 7);
    CancelOrderResponseDecoder.validate(response, 0);
    assertEquals(1001L, CancelOrderResponseDecoder.timestampNanos(response, 0));
    assertEquals(1003L, CancelOrderResponseDecoder.clientOrderId(response, 0));
    assertEquals(1005L, CancelOrderResponseDecoder.orderId(response, 0));
    assertEquals(1007L, CancelOrderResponseDecoder.receiveTimeNanos(response, 0));

    ByteBuffer reject = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(reject, 0, 0, 85, 222, 11, 11L, 10L, 100L);
    int body = 32;
    reject.putLong(body, 2001L);
    reject.putLong(body + 8, 2002L);
    reject.putLong(body + 16, 2003L);
    reject.putLong(body + 24, 2004L);
    reject.putLong(body + 32, Long.MIN_VALUE);
    reject.putLong(body + 40, 2006L);
    reject.put(body + 48, (byte) 8);
    putDetails(reject, body + 49, "ioc");
    TcpHeaderCodec.zeroPadding(reject, 0, 85);
    CancelOrderRejectDecoder.validate(reject, 0);
    assertTrue(CancelOrderRejectDecoder.isOrderIdNull(reject, 0));
    assertEquals(8, CancelOrderRejectDecoder.reason(reject, 0));
    assertEquals('o', CancelOrderRejectDecoder.detailsByte(reject, 0, 1));
  }

  public void testMassCancelResponseAndRejectPinCountsReasonsAndDetails() {
    ByteBuffer response = fixedFrame(240, 68, 4);
    response.putInt(32 + 32, 17);
    TcpHeaderCodec.zeroPadding(response, 0, 68);
    MassCancelResponseDecoder.validate(response, 0);
    assertEquals(1001L, MassCancelResponseDecoder.timestampNanos(response, 0));
    assertEquals(1003L, MassCancelResponseDecoder.correlationId(response, 0));
    assertEquals(1004L, MassCancelResponseDecoder.receiveTimeNanos(response, 0));
    assertEquals(17, MassCancelResponseDecoder.totalOrderCount(response, 0));

    ByteBuffer reject = ByteBuffer.allocateDirect(96).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(reject, 0, 0, 62, 242, 11, 12L, 11L, 101L);
    reject.putLong(32, 3001L);
    reject.putLong(40, 3002L);
    reject.putLong(48, 3003L);
    reject.put(56, (byte) 3);
    putDetails(reject, 57, "deny");
    TcpHeaderCodec.zeroPadding(reject, 0, 62);
    MassCancelRejectDecoder.validate(reject, 0);
    assertEquals(3003L, MassCancelRejectDecoder.correlationId(reject, 0));
    assertEquals(3, MassCancelRejectDecoder.reason(reject, 0));
    assertEquals(4, MassCancelRejectDecoder.detailsLength(reject, 0));
    assertEquals('y', MassCancelRejectDecoder.detailsByte(reject, 0, 3));
  }

  public void testInvalidIdentifiersScopesEnumsCountsAndDetailsFailClosed() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MassCancelRequestEncoder.encode(
                frame,
                0,
                1L,
                Long.MIN_VALUE,
                Long.MIN_VALUE,
                0,
                0,
                1L,
                0L,
                1L));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CancelOrderByIdRequestEncoder.encode(
                frame, 0, -1L, 1L, 2L, 1L, 0L, 1L));

    ByteBuffer response = fixedFrame(240, 68, 4);
    response.putInt(32 + 32, -1);
    assertThrows(
        StarbaseProtocolException.class, () -> MassCancelResponseDecoder.validate(response, 0));

    ByteBuffer reject = ByteBuffer.allocateDirect(96).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(reject, 0, 0, 59, 242, 11, 1L, 0L, 1L);
    reject.put(56, (byte) 4);
    reject.put(57, (byte) 1);
    reject.put(58, (byte) 0);
    TcpHeaderCodec.zeroPadding(reject, 0, 59);
    assertThrows(
        StarbaseProtocolException.class, () -> MassCancelRejectDecoder.validate(reject, 0));
  }

  public void testValidCancelDecodeAllocatesNothingAfterWarmup() {
    ByteBuffer request = ByteBuffer.allocateDirect(96).order(ByteOrder.LITTLE_ENDIAN);
    CancelOrderRequestEncoder.encode(request, 0, 1L, 2L, 3L, 1L, 0L, 1L);
    ByteBuffer response = fixedFrame(220, 88, 7);
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      CancelOrderRequestDecoder.validate(request, 0);
      CancelOrderResponseDecoder.validate(response, 0);
      sink += CancelOrderRequestDecoder.clientOrderId(request, 0);
      sink += CancelOrderResponseDecoder.orderId(response, 0);
    }
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      CancelOrderRequestDecoder.validate(request, 0);
      CancelOrderResponseDecoder.validate(response, 0);
      sink += CancelOrderRequestDecoder.clientOrderId(request, 0);
      sink += CancelOrderResponseDecoder.orderId(response, 0);
    }
    assertEquals(0L, bean.getThreadAllocatedBytes(threadId) - before);
  }

  private static ByteBuffer fixedFrame(int templateId, int messageLength, int bodyLongs) {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(frame, 0, 0, messageLength, templateId, 11, 10L, 9L, 100L);
    for (int index = 0; index < bodyLongs; index++) {
      frame.putLong(32 + index * 8, 1001L + index);
    }
    TcpHeaderCodec.zeroPadding(frame, 0, messageLength);
    return frame;
  }

  private static void putDetails(ByteBuffer frame, int offset, String value) {
    frame.put(offset, (byte) value.length());
    for (int index = 0; index < value.length(); index++) {
      frame.put(offset + 1 + index, (byte) value.charAt(index));
    }
  }
}
