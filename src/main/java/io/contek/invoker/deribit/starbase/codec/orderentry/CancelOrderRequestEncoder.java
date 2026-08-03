package io.contek.invoker.deribit.starbase.codec.orderentry;

import java.nio.ByteBuffer;

/** Hardcoded encoder for order-entry schema-v11 CancelOrderRequest (template 120). */
public final class CancelOrderRequestEncoder {

  public static final int TEMPLATE_ID = 120;
  public static final int BODY_LENGTH = 24;
  public static final int MESSAGE_LENGTH = SessionCodecSupport.BODY_OFFSET + BODY_LENGTH;
  public static final int ENCODED_LENGTH = 56;

  public static int encode(
      ByteBuffer buffer,
      int offset,
      long clientOrderId,
      long correlationId,
      long instrumentId,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    validateArguments(clientOrderId, instrumentId);
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
    buffer.putLong(body, clientOrderId);
    buffer.putLong(body + 8, correlationId);
    buffer.putLong(body + 16, instrumentId);
    SessionCodecSupport.finishEncode(buffer, offset, MESSAGE_LENGTH);
    return encoded;
  }

  public static void validateArguments(long clientOrderId, long instrumentId) {
    if (clientOrderId == Long.MIN_VALUE || instrumentId < 0) {
      throw new IllegalArgumentException(
          "clientOrderId must not be SBE null and instrumentId must be non-negative");
    }
  }

  private CancelOrderRequestEncoder() {}
}
