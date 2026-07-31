package io.contek.invoker.deribit.starbase.codec.orderentry;

import java.nio.ByteBuffer;

public final class GapFillEncoder {

  public static int encode(
      ByteBuffer buffer,
      int offset,
      long newSequenceNumber,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    if (newSequenceNumber < 1) {
      throw new IllegalArgumentException("newSequenceNumber must be positive");
    }
    int encoded =
        SessionCodecSupport.encodeHeader(
            buffer,
            offset,
            GapFillDecoder.TEMPLATE_ID,
            GapFillDecoder.BODY_LENGTH,
            sequence,
            lastProcessedSequence,
            sendTimeNanos);
    buffer.putLong(offset + SessionCodecSupport.BODY_OFFSET, newSequenceNumber);
    SessionCodecSupport.finishEncode(buffer, offset, 40);
    return encoded;
  }

  private GapFillEncoder() {}
}
