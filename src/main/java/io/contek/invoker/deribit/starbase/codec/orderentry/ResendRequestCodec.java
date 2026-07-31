package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

public final class ResendRequestCodec {

  public static final int TEMPLATE_ID = 20;
  public static final int BODY_LENGTH = 16;

  public static int encode(
      ByteBuffer buffer,
      int offset,
      long fromSequence,
      long toSequence,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    if (fromSequence < 1 || (toSequence != 0 && toSequence < fromSequence)) {
      throw new IllegalArgumentException("invalid resend sequence range");
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
    buffer.putLong(body, fromSequence);
    buffer.putLong(body + 8, toSequence);
    SessionCodecSupport.finishEncode(buffer, offset, 48);
    return encoded;
  }

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(buffer, offset, TEMPLATE_ID, BODY_LENGTH);
    long from = fromSequenceNumber(buffer, offset);
    long to = toSequenceNumber(buffer, offset);
    if (from < 1 || (to != 0 && to < from)) {
      throw new StarbaseProtocolException("invalid ResendRequest sequence range");
    }
  }

  public static long fromSequenceNumber(ByteBuffer buffer, int offset) {
    return buffer.getLong(offset + SessionCodecSupport.BODY_OFFSET);
  }

  public static long toSequenceNumber(ByteBuffer buffer, int offset) {
    return buffer.getLong(offset + SessionCodecSupport.BODY_OFFSET + 8);
  }

  private ResendRequestCodec() {}
}
