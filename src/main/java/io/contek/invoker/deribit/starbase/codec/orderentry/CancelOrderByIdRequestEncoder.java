package io.contek.invoker.deribit.starbase.codec.orderentry;

import java.nio.ByteBuffer;

/** Hardcoded encoder for order-entry schema-v11 CancelOrderByIdRequest (template 125). */
public final class CancelOrderByIdRequestEncoder {

  public static final int TEMPLATE_ID = 125;
  public static final int BODY_LENGTH = 24;

  public static int encode(
      ByteBuffer buffer,
      int offset,
      long orderId,
      long correlationId,
      long instrumentId,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    if (orderId < 0 || instrumentId < 0) {
      throw new IllegalArgumentException("cancel-by-ID identifiers must be non-negative");
    }
    int encoded =
        SessionCodecSupport.encodeHeader(
            buffer,
            offset,
            TEMPLATE_ID,
            BODY_LENGTH,
            sequence,
            lastProcessedSequence,
            sendTimeNanos);
    int body = offset + SessionCodecSupport.BODY_OFFSET;
    buffer.putLong(body, orderId);
    buffer.putLong(body + 8, correlationId);
    buffer.putLong(body + 16, instrumentId);
    SessionCodecSupport.finishEncode(buffer, offset, 56);
    return encoded;
  }

  private CancelOrderByIdRequestEncoder() {}
}
