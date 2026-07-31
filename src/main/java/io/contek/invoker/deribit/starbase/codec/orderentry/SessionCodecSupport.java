package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.codec.common.OrderEntryTemplateDispatch;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.WirePrimitives;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import io.contek.invoker.deribit.starbase.protocol.ProtocolSchemas;
import java.nio.ByteBuffer;

final class SessionCodecSupport {

  static final int BODY_OFFSET = TcpHeaderCodec.ENCODED_LENGTH;

  static int encodeHeader(
      ByteBuffer buffer,
      int offset,
      int templateId,
      int bodyLength,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    int messageLength = BODY_OFFSET + bodyLength;
    int encodedLength = WirePrimitives.align8(messageLength);
    WirePrimitives.requireLittleEndian(buffer);
    WirePrimitives.requireBounds(buffer, offset, encodedLength);
    TcpHeaderCodec.encode(
        buffer,
        offset,
        0,
        messageLength,
        templateId,
        ProtocolSchemas.ORDER_ENTRY.version(),
        sequence,
        lastProcessedSequence,
        sendTimeNanos);
    return encodedLength;
  }

  static void finishEncode(
      ByteBuffer buffer, int offset, int messageLength) {
    TcpHeaderCodec.zeroPadding(buffer, offset, messageLength);
  }

  static int validateFixed(
      ByteBuffer buffer, int offset, int templateId, int bodyLength) {
    int encodedLength = OrderEntryTemplateDispatch.validateFrame(buffer, offset);
    int expectedLength = BODY_OFFSET + bodyLength;
    requireTemplateAndLength(buffer, offset, templateId, expectedLength);
    return encodedLength;
  }

  static int validateVariable(
      ByteBuffer buffer, int offset, int templateId, int fixedBodyLength) {
    int encodedLength = OrderEntryTemplateDispatch.validateFrame(buffer, offset);
    int messageLength = TcpHeaderCodec.messageLength(buffer, offset);
    if (messageLength < BODY_OFFSET + fixedBodyLength + 1) {
      throw new StarbaseProtocolException("truncated variable session message");
    }
    requireTemplate(buffer, offset, templateId);
    int dataLength =
        Byte.toUnsignedInt(buffer.get(offset + BODY_OFFSET + fixedBodyLength));
    if (messageLength != BODY_OFFSET + fixedBodyLength + 1 + dataLength) {
      throw new StarbaseProtocolException("session variable-data length mismatch");
    }
    validateAsciiBytes(
        buffer, offset + BODY_OFFSET + fixedBodyLength + 1, dataLength, true);
    return encodedLength;
  }

  static void putFixedAscii(
      ByteBuffer buffer, int offset, char[] value, int width, String field) {
    if (value == null || value.length == 0 || value.length > width) {
      throw new IllegalArgumentException(field + " has invalid length");
    }
    validateAsciiChars(value, field);
    for (int index = 0; index < width; index++) {
      byte encoded = 0;
      if (index < value.length) {
        encoded = (byte) value[index];
      }
      buffer.put(offset + index, encoded);
    }
  }

  static void validateFixedAscii(
      ByteBuffer buffer, int offset, int width, String field) {
    int length = 0;
    boolean zeroSeen = false;
    for (int index = 0; index < width; index++) {
      int value = Byte.toUnsignedInt(buffer.get(offset + index));
      if (value == 0) {
        zeroSeen = true;
      } else {
        if (zeroSeen || value < 0x20 || value > 0x7E) {
          throw new StarbaseProtocolException("invalid fixed ASCII " + field);
        }
        length++;
      }
    }
    if (length == 0) {
      throw new StarbaseProtocolException("empty fixed ASCII " + field);
    }
  }

  static int putVariableAscii(
      ByteBuffer buffer, int offset, char[] value, boolean allowEmpty, String field) {
    if (value == null || value.length > 255 || (!allowEmpty && value.length == 0)) {
      throw new IllegalArgumentException(field + " has invalid length");
    }
    validateAsciiChars(value, field);
    buffer.put(offset, (byte) value.length);
    for (int index = 0; index < value.length; index++) {
      buffer.put(offset + 1 + index, (byte) value[index]);
    }
    return value.length + 1;
  }

  static int variableLength(ByteBuffer buffer, int offset, int fixedBodyLength) {
    return Byte.toUnsignedInt(buffer.get(offset + BODY_OFFSET + fixedBodyLength));
  }

  static int variableByte(
      ByteBuffer buffer, int offset, int fixedBodyLength, int index) {
    int length = variableLength(buffer, offset, fixedBodyLength);
    if (index < 0 || index >= length) {
      throw new IndexOutOfBoundsException(index);
    }
    return Byte.toUnsignedInt(
        buffer.get(offset + BODY_OFFSET + fixedBodyLength + 1 + index));
  }

  static void requireNonNegative(long value, String field) {
    if (value < 0) {
      throw new StarbaseProtocolException("negative session " + field);
    }
  }

  private static void requireTemplateAndLength(
      ByteBuffer buffer, int offset, int templateId, int expectedLength) {
    requireTemplate(buffer, offset, templateId);
    int actualLength = TcpHeaderCodec.messageLength(buffer, offset);
    if (actualLength != expectedLength) {
      throw new StarbaseProtocolException(
          "session message length mismatch: expected="
              + expectedLength
              + ", actual="
              + actualLength);
    }
  }

  private static void requireTemplate(
      ByteBuffer buffer, int offset, int templateId) {
    int actual = TcpHeaderCodec.messageTypeId(buffer, offset);
    if (actual != templateId) {
      throw new StarbaseProtocolException(
          "session template mismatch: expected=" + templateId + ", actual=" + actual);
    }
  }

  private static void validateAsciiBytes(
      ByteBuffer buffer, int offset, int length, boolean allowEmpty) {
    if (!allowEmpty && length == 0) {
      throw new StarbaseProtocolException("empty session ASCII data");
    }
    for (int index = 0; index < length; index++) {
      int value = Byte.toUnsignedInt(buffer.get(offset + index));
      if (value < 0x20 || value > 0x7E) {
        throw new StarbaseProtocolException("invalid session ASCII data");
      }
    }
  }

  private static void validateAsciiChars(char[] value, String field) {
    for (int index = 0; index < value.length; index++) {
      char character = value[index];
      if (character < 0x20 || character > 0x7E) {
        throw new IllegalArgumentException(field + " is not printable ASCII");
      }
    }
  }

  private SessionCodecSupport() {}
}
