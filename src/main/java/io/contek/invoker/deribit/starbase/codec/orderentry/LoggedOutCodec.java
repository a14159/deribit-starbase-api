package io.contek.invoker.deribit.starbase.codec.orderentry;

import java.nio.ByteBuffer;

public final class LoggedOutCodec {

  public static final int TEMPLATE_ID = 5;

  public static int encode(
      ByteBuffer buffer,
      int offset,
      char[] reason,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    int reasonLength = reason == null ? -1 : reason.length;
    if (reasonLength < 0 || reasonLength > 255) {
      throw new IllegalArgumentException("reason has invalid length");
    }
    int bodyLength = 1 + reasonLength;
    int encoded =
        SessionCodecSupport.encodeHeader(
            buffer,
            offset,
            TEMPLATE_ID,
            bodyLength,
            sequence,
            lastProcessedSequence,
            sendTimeNanos);
    SessionCodecSupport.putVariableAscii(
        buffer, offset + SessionCodecSupport.BODY_OFFSET, reason, true, "reason");
    SessionCodecSupport.finishEncode(
        buffer, offset, SessionCodecSupport.BODY_OFFSET + bodyLength);
    return encoded;
  }

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateVariable(buffer, offset, TEMPLATE_ID, 0);
  }

  public static int reasonLength(ByteBuffer buffer, int offset) {
    return SessionCodecSupport.variableLength(buffer, offset, 0);
  }

  public static int reasonByte(ByteBuffer buffer, int offset, int index) {
    return SessionCodecSupport.variableByte(buffer, offset, 0, index);
  }

  private LoggedOutCodec() {}
}
