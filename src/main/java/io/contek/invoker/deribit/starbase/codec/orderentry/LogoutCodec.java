package io.contek.invoker.deribit.starbase.codec.orderentry;

import java.nio.ByteBuffer;

public final class LogoutCodec {

  public static final int TEMPLATE_ID = 4;

  public static int encode(
      ByteBuffer buffer,
      int offset,
      char[] reason,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    int bodyLength = 1 + requireReasonLength(reason);
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

  private static int requireReasonLength(char[] reason) {
    if (reason == null || reason.length > 255) {
      throw new IllegalArgumentException("reason has invalid length");
    }
    return reason.length;
  }

  private LogoutCodec() {}
}
