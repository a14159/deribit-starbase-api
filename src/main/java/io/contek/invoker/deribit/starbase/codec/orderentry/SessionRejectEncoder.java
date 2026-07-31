package io.contek.invoker.deribit.starbase.codec.orderentry;

import java.nio.ByteBuffer;

public final class SessionRejectEncoder {

  public static int encode(
      ByteBuffer buffer,
      int offset,
      long refSequenceNumber,
      int reason,
      char[] details,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    if (refSequenceNumber < 1 || reason < 1 || reason > 5) {
      throw new IllegalArgumentException("invalid session reject reference/reason");
    }
    if (details == null || details.length > 255) {
      throw new IllegalArgumentException("details has invalid length");
    }
    int bodyLength = SessionRejectDecoder.FIXED_BODY_LENGTH + 1 + details.length;
    int encoded =
        SessionCodecSupport.encodeHeader(
            buffer,
            offset,
            SessionRejectDecoder.TEMPLATE_ID,
            bodyLength,
            sequence,
            lastProcessedSequence,
            sendTimeNanos);
    int body = offset + SessionCodecSupport.BODY_OFFSET;
    buffer.putLong(body, refSequenceNumber);
    buffer.put(body + 8, (byte) reason);
    SessionCodecSupport.putVariableAscii(
        buffer, body + SessionRejectDecoder.FIXED_BODY_LENGTH, details, true, "details");
    SessionCodecSupport.finishEncode(
        buffer, offset, SessionCodecSupport.BODY_OFFSET + bodyLength);
    return encoded;
  }

  private SessionRejectEncoder() {}
}
