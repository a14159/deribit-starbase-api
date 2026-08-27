package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import io.contek.invoker.deribit.starbase.protocol.ProtocolSchemas;
import java.nio.ByteBuffer;

public final class LogonConfirmationCodec {

  public static final int TEMPLATE_ID = 2;
  public static final int BODY_LENGTH = 6;

  public static int encode(
      ByteBuffer buffer,
      int offset,
      int heartbeatIntervalSeconds,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    return encode(
        buffer,
        offset,
        heartbeatIntervalSeconds,
        ProtocolSchemas.ORDER_ENTRY.version(),
        sequence,
        lastProcessedSequence,
        sendTimeNanos);
  }

  public static int encode(
      ByteBuffer buffer,
      int offset,
      int heartbeatIntervalSeconds,
      int schemaVersion,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    if (heartbeatIntervalSeconds < 1) {
      throw new IllegalArgumentException("heartbeatIntervalSeconds must be positive");
    }
    if (schemaVersion < 14 || schemaVersion > ProtocolSchemas.ORDER_ENTRY.version()) {
      throw new IllegalArgumentException("schemaVersion must be a current production/testnet version");
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
    buffer.putShort(offset + SessionCodecSupport.BODY_OFFSET + 4, (short) schemaVersion);
    SessionCodecSupport.finishEncode(buffer, offset, 38);
    return encoded;
  }

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(buffer, offset, TEMPLATE_ID, BODY_LENGTH);
    if (heartbeatIntervalSeconds(buffer, offset) < 1) {
      throw new StarbaseProtocolException("invalid LogonConf heartbeat interval");
    }
    int schemaVersion = schemaVersion(buffer, offset);
    if (schemaVersion < 14 || schemaVersion > ProtocolSchemas.ORDER_ENTRY.version()) {
      throw new StarbaseProtocolException("unsupported LogonConf schemaVersion");
    }
  }

  public static int heartbeatIntervalSeconds(ByteBuffer buffer, int offset) {
    return buffer.getInt(offset + SessionCodecSupport.BODY_OFFSET);
  }

  public static int schemaVersion(ByteBuffer buffer, int offset) {
    return Short.toUnsignedInt(
        buffer.getShort(offset + SessionCodecSupport.BODY_OFFSET + 4));
  }

  private LogonConfirmationCodec() {}
}
