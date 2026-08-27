package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.protocol.ProtocolSchemas;
import java.nio.ByteBuffer;

public final class LogonEncoder {

  public static final int TEMPLATE_ID = 1;
  public static final int BODY_LENGTH = 68;
  public static final int MESSAGE_LENGTH = 100;

  public static int encode(
      ByteBuffer buffer,
      int offset,
      char[] clientId,
      char[] secret,
      boolean resetSequenceNumber,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    return encode(
        buffer,
        offset,
        clientId,
        secret,
        resetSequenceNumber,
        ProtocolSchemas.ORDER_ENTRY.version(),
        false,
        sequence,
        lastProcessedSequence,
        sendTimeNanos);
  }

  public static int encode(
      ByteBuffer buffer,
      int offset,
      char[] clientId,
      char[] secret,
      boolean resetSequenceNumber,
      int schemaVersion,
      boolean cancelOnDisconnect,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
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
    int body = offset + SessionCodecSupport.BODY_OFFSET;
    SessionCodecSupport.putFixedAscii(buffer, body, clientId, 16, "clientId");
    SessionCodecSupport.putFixedAscii(buffer, body + 16, secret, 48, "secret");
    buffer.put(body + 64, (byte) (resetSequenceNumber ? 1 : 0));
    buffer.putShort(body + 65, (short) schemaVersion);
    buffer.put(body + 67, (byte) (cancelOnDisconnect ? 1 : 0));
    SessionCodecSupport.finishEncode(buffer, offset, MESSAGE_LENGTH);
    return encoded;
  }

  private LogonEncoder() {}
}
