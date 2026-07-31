package io.contek.invoker.deribit.starbase.codec.orderentry;

import java.nio.ByteBuffer;

public final class TestRequestCodec {

  public static final int TEMPLATE_ID = 11;
  public static final int BODY_LENGTH = 8;

  public static int encode(
      ByteBuffer buffer,
      int offset,
      long correlationId,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    int encoded =
        SessionCodecSupport.encodeHeader(
            buffer,
            offset,
            TEMPLATE_ID,
            BODY_LENGTH,
            sequence,
            lastProcessedSequence,
            sendTimeNanos);
    buffer.putLong(offset + SessionCodecSupport.BODY_OFFSET, correlationId);
    SessionCodecSupport.finishEncode(buffer, offset, 40);
    return encoded;
  }

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(buffer, offset, TEMPLATE_ID, BODY_LENGTH);
    SessionCodecSupport.requireNonNegative(correlationId(buffer, offset), "correlationId");
  }

  public static long correlationId(ByteBuffer buffer, int offset) {
    return buffer.getLong(offset + SessionCodecSupport.BODY_OFFSET);
  }

  private TestRequestCodec() {}
}
