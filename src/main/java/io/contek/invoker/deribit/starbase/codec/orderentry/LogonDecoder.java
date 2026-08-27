package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

public final class LogonDecoder {

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(
        buffer, offset, LogonEncoder.TEMPLATE_ID, LogonEncoder.BODY_LENGTH);
    int body = offset + SessionCodecSupport.BODY_OFFSET;
    SessionCodecSupport.validateFixedAscii(buffer, body, 16, "clientId");
    SessionCodecSupport.validateFixedAscii(buffer, body + 16, 48, "secret");
    int reset = Byte.toUnsignedInt(buffer.get(body + 64));
    if (reset > 1) {
      throw new StarbaseProtocolException("invalid Logon resetSeqNum");
    }
    int schemaVersion = schemaVersion(buffer, offset);
    if (schemaVersion < 14 || schemaVersion > 15) {
      throw new StarbaseProtocolException("unsupported Logon schemaVersion");
    }
    if (cancelOnDisconnect(buffer, offset) > 1) {
      throw new StarbaseProtocolException("invalid Logon cancelOnDisconnect");
    }
  }

  public static int clientIdByte(
      ByteBuffer buffer, int offset, int index) {
    requireIndex(index, 16);
    return Byte.toUnsignedInt(
        buffer.get(offset + SessionCodecSupport.BODY_OFFSET + index));
  }

  public static int secretByte(ByteBuffer buffer, int offset, int index) {
    requireIndex(index, 48);
    return Byte.toUnsignedInt(
        buffer.get(offset + SessionCodecSupport.BODY_OFFSET + 16 + index));
  }

  public static int resetSequenceNumber(ByteBuffer buffer, int offset) {
    return Byte.toUnsignedInt(
        buffer.get(offset + SessionCodecSupport.BODY_OFFSET + 64));
  }

  public static int schemaVersion(ByteBuffer buffer, int offset) {
    return Short.toUnsignedInt(
        buffer.getShort(offset + SessionCodecSupport.BODY_OFFSET + 65));
  }

  public static int cancelOnDisconnect(ByteBuffer buffer, int offset) {
    return Byte.toUnsignedInt(
        buffer.get(offset + SessionCodecSupport.BODY_OFFSET + 67));
  }

  private static void requireIndex(int index, int length) {
    if (index < 0 || index >= length) {
      throw new IndexOutOfBoundsException(index);
    }
  }

  private LogonDecoder() {}
}
