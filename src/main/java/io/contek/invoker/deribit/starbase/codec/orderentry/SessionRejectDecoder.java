package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

public final class SessionRejectDecoder {

  public static final int TEMPLATE_ID = 30;
  public static final int FIXED_BODY_LENGTH = 9;

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateVariable(
        buffer, offset, TEMPLATE_ID, FIXED_BODY_LENGTH);
    if (refSequenceNumber(buffer, offset) < 1) {
      throw new StarbaseProtocolException("invalid Reject refSequenceNumber");
    }
    int reason = reason(buffer, offset);
    if (reason < 1 || reason > 6) {
      throw new StarbaseProtocolException("unknown RejectReason: " + reason);
    }
  }

  public static long refSequenceNumber(ByteBuffer buffer, int offset) {
    return buffer.getLong(offset + SessionCodecSupport.BODY_OFFSET);
  }

  public static int reason(ByteBuffer buffer, int offset) {
    return Byte.toUnsignedInt(
        buffer.get(offset + SessionCodecSupport.BODY_OFFSET + 8));
  }

  public static int detailsLength(ByteBuffer buffer, int offset) {
    return SessionCodecSupport.variableLength(buffer, offset, FIXED_BODY_LENGTH);
  }

  public static int detailsByte(ByteBuffer buffer, int offset, int index) {
    return SessionCodecSupport.variableByte(buffer, offset, FIXED_BODY_LENGTH, index);
  }

  private SessionRejectDecoder() {}
}
