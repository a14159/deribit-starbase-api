package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry schema-v11 CancelOrderByIdRequest (template 125). */
public final class CancelOrderByIdRequestDecoder {

  public static final int TEMPLATE_ID = 125;
  public static final int BODY_LENGTH = 24;

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(buffer, offset, TEMPLATE_ID, BODY_LENGTH);
    if (orderId(buffer, offset) == Long.MIN_VALUE || instrumentId(buffer, offset) < 0) {
      throw new StarbaseProtocolException("null orderId or negative instrumentId");
    }
  }

  public static long orderId(ByteBuffer buffer, int offset) {
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

  private CancelOrderByIdRequestDecoder() {}
}
