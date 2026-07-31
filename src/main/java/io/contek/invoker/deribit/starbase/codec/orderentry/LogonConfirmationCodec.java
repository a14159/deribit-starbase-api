package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

public final class LogonConfirmationCodec {

  public static final int TEMPLATE_ID = 2;
  public static final int BODY_LENGTH = 4;

  public static int encode(
      ByteBuffer buffer,
      int offset,
      int heartbeatIntervalSeconds,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    if (heartbeatIntervalSeconds < 1) {
      throw new IllegalArgumentException("heartbeatIntervalSeconds must be positive");
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
    buffer.putInt(offset + SessionCodecSupport.BODY_OFFSET, heartbeatIntervalSeconds);
    SessionCodecSupport.finishEncode(buffer, offset, 36);
    return encoded;
  }

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(buffer, offset, TEMPLATE_ID, BODY_LENGTH);
    if (heartbeatIntervalSeconds(buffer, offset) < 1) {
      throw new StarbaseProtocolException("invalid LogonConf heartbeat interval");
    }
  }

  public static int heartbeatIntervalSeconds(ByteBuffer buffer, int offset) {
    return buffer.getInt(offset + SessionCodecSupport.BODY_OFFSET);
  }

  private LogonConfirmationCodec() {}
}
