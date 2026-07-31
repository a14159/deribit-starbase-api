package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry schema-v11 MassCancelResponse (template 240). */
public final class MassCancelResponseDecoder {

  public static final int TEMPLATE_ID = 240;
  public static final int BODY_LENGTH = 36;

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(buffer, offset, TEMPLATE_ID, BODY_LENGTH);
    if (totalOrderCount(buffer, offset) < 0) {
      throw new StarbaseProtocolException("negative mass-cancel order count");
    }
  }

  public static long timestampNanos(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset));
  }

  public static long execId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 8);
  }

  public static long correlationId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 16);
  }

  public static long receiveTimeNanos(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 24);
  }

  public static int totalOrderCount(ByteBuffer buffer, int offset) {
    return buffer.getInt(body(offset) + 32);
  }

  private static int body(int offset) {
    return offset + SessionCodecSupport.BODY_OFFSET;
  }

  private MassCancelResponseDecoder() {}
}
