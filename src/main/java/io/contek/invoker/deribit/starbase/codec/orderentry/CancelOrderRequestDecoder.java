package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry schema-v11 CancelOrderRequest (template 120). */
public final class CancelOrderRequestDecoder {

  public static final int TEMPLATE_ID = CancelOrderRequestEncoder.TEMPLATE_ID;
  public static final int BODY_LENGTH = CancelOrderRequestEncoder.BODY_LENGTH;

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(buffer, offset, TEMPLATE_ID, BODY_LENGTH);
    if (clientOrderId(buffer, offset) < 0 || instrumentId(buffer, offset) < 0) {
      throw new StarbaseProtocolException("negative cancel identifier");
    }
  }

  public static long clientOrderId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset));
  }

  public static long correlationId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 8);
  }

  public static long instrumentId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 16);
  }

  private static int body(int offset) {
    return offset + SessionCodecSupport.BODY_OFFSET;
  }

  private CancelOrderRequestDecoder() {}
}
