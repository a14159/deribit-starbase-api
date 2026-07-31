package io.contek.invoker.deribit.starbase.codec.orderentry;

import java.nio.ByteBuffer;

public final class LogonEncoder {

  public static final int TEMPLATE_ID = 1;
  public static final int BODY_LENGTH = 65;
  public static final int MESSAGE_LENGTH = 97;

  public static int encode(
      ByteBuffer buffer,
      int offset,
      char[] clientId,
      char[] secret,
      boolean resetSequenceNumber,
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
    int body = offset + SessionCodecSupport.BODY_OFFSET;
    SessionCodecSupport.putFixedAscii(buffer, body, clientId, 16, "clientId");
    SessionCodecSupport.putFixedAscii(buffer, body + 16, secret, 48, "secret");
    buffer.put(body + 64, (byte) (resetSequenceNumber ? 1 : 0));
    SessionCodecSupport.finishEncode(buffer, offset, MESSAGE_LENGTH);
    return encoded;
  }

  private LogonEncoder() {}
}
