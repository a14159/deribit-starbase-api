package io.contek.invoker.deribit.starbase.codec.orderentry;

import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry schema-v11 CancelOrderResponse (template 220). */
public final class CancelOrderResponseDecoder {

  public static final int TEMPLATE_ID = 220;
  public static final int BODY_LENGTH = 56;

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(buffer, offset, TEMPLATE_ID, BODY_LENGTH);
  }

  public static long timestampNanos(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset));
  }

  public static long execId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 8);
  }

  public static long clientOrderId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 16);
  }

  public static long correlationId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 24);
  }

  public static long orderId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 32);
  }

  public static long instrumentId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 40);
  }

  public static long receiveTimeNanos(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 48);
  }

  private static int body(int offset) {
    return offset + SessionCodecSupport.BODY_OFFSET;
  }

  private CancelOrderResponseDecoder() {}
}
