package io.contek.invoker.deribit.starbase.codec.common;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded Deribit Starbase TCP message header codec (order schema 2101 v11). */
public final class TcpHeaderCodec {

  public static final int ENCODED_LENGTH = 32;
  public static final int PROTOCOL_ID = 0xDB;
  public static final int FLAG_RESEND = 1;

  public static final int PROTOCOL_ID_OFFSET = 0;
  public static final int FLAGS_OFFSET = 1;
  public static final int MESSAGE_LENGTH_OFFSET = 2;
  public static final int MESSAGE_TYPE_ID_OFFSET = 4;
  public static final int VERSION_OFFSET = 6;
  public static final int SEQUENCE_NUMBER_OFFSET = 8;
  public static final int LAST_PROCESSED_SEQUENCE_NUMBER_OFFSET = 16;
  public static final int SEND_TIME_NANOS_OFFSET = 24;

  public static void encode(
      ByteBuffer buffer,
      int headerOffset,
      int flags,
      int messageLength,
      int messageTypeId,
      int version,
      long sequenceNumber,
      long lastProcessedSequenceNumber,
      long sendTimeNanos) {
    validateFlagsForEncode(flags);
    validateMessageLengthForEncode(messageLength);
    validateUInt16(messageTypeId, "messageTypeId");
    validateUInt16(version, "version");
    WirePrimitives.requireLittleEndian(buffer);
    WirePrimitives.requireBounds(buffer, headerOffset, ENCODED_LENGTH);
    buffer.put(headerOffset + PROTOCOL_ID_OFFSET, (byte) PROTOCOL_ID);
    buffer.put(headerOffset + FLAGS_OFFSET, (byte) flags);
    buffer.putShort(headerOffset + MESSAGE_LENGTH_OFFSET, (short) messageLength);
    buffer.putShort(headerOffset + MESSAGE_TYPE_ID_OFFSET, (short) messageTypeId);
    buffer.putShort(headerOffset + VERSION_OFFSET, (short) version);
    buffer.putLong(headerOffset + SEQUENCE_NUMBER_OFFSET, sequenceNumber);
    buffer.putLong(
        headerOffset + LAST_PROCESSED_SEQUENCE_NUMBER_OFFSET, lastProcessedSequenceNumber);
    buffer.putLong(headerOffset + SEND_TIME_NANOS_OFFSET, sendTimeNanos);
  }

  public static void validateHeader(ByteBuffer buffer, int headerOffset) {
    WirePrimitives.requireLittleEndian(buffer);
    WirePrimitives.requireBounds(buffer, headerOffset, ENCODED_LENGTH);
    int protocol = Byte.toUnsignedInt(buffer.get(headerOffset + PROTOCOL_ID_OFFSET));
    if (protocol != PROTOCOL_ID) {
      throw new StarbaseProtocolException("invalid TCP protocolId: " + protocol);
    }
    int headerFlags = Byte.toUnsignedInt(buffer.get(headerOffset + FLAGS_OFFSET));
    if ((headerFlags & ~FLAG_RESEND) != 0) {
      throw new StarbaseProtocolException("unsupported TCP header flags: " + headerFlags);
    }
    int length = Short.toUnsignedInt(buffer.getShort(headerOffset + MESSAGE_LENGTH_OFFSET));
    if (length < ENCODED_LENGTH) {
      throw new StarbaseProtocolException("invalid TCP messageLength: " + length);
    }
  }

  /** Validates a complete padded frame and returns its aligned encoded length. */
  public static int validateFrame(ByteBuffer buffer, int headerOffset) {
    validateHeader(buffer, headerOffset);
    int length = messageLengthUnchecked(buffer, headerOffset);
    int alignedLength = WirePrimitives.align8(length);
    WirePrimitives.requireBounds(buffer, headerOffset, alignedLength);
    for (int index = length; index < alignedLength; index++) {
      if (buffer.get(headerOffset + index) != 0) {
        throw new StarbaseProtocolException("non-zero TCP padding at frame offset " + index);
      }
    }
    return alignedLength;
  }

  public static void zeroPadding(ByteBuffer buffer, int headerOffset, int messageLength) {
    validateMessageLengthForEncode(messageLength);
    WirePrimitives.requireLittleEndian(buffer);
    int alignedLength = WirePrimitives.align8(messageLength);
    WirePrimitives.requireBounds(buffer, headerOffset, alignedLength);
    for (int index = messageLength; index < alignedLength; index++) {
      buffer.put(headerOffset + index, (byte) 0);
    }
  }

  public static int protocolId(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return Byte.toUnsignedInt(buffer.get(headerOffset + PROTOCOL_ID_OFFSET));
  }

  public static int flags(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return Byte.toUnsignedInt(buffer.get(headerOffset + FLAGS_OFFSET));
  }

  public static int messageLength(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return messageLengthUnchecked(buffer, headerOffset);
  }

  public static int messageTypeId(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return Short.toUnsignedInt(buffer.getShort(headerOffset + MESSAGE_TYPE_ID_OFFSET));
  }

  public static int version(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return Short.toUnsignedInt(buffer.getShort(headerOffset + VERSION_OFFSET));
  }

  public static long sequenceNumber(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return buffer.getLong(headerOffset + SEQUENCE_NUMBER_OFFSET);
  }

  public static long lastProcessedSequenceNumber(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return buffer.getLong(headerOffset + LAST_PROCESSED_SEQUENCE_NUMBER_OFFSET);
  }

  public static long sendTimeNanos(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return buffer.getLong(headerOffset + SEND_TIME_NANOS_OFFSET);
  }

  private static void requireHeader(ByteBuffer buffer, int headerOffset) {
    WirePrimitives.requireLittleEndian(buffer);
    WirePrimitives.requireBounds(buffer, headerOffset, ENCODED_LENGTH);
  }

  private static int messageLengthUnchecked(ByteBuffer buffer, int headerOffset) {
    return Short.toUnsignedInt(buffer.getShort(headerOffset + MESSAGE_LENGTH_OFFSET));
  }

  private static void validateMessageLengthForEncode(int messageLength) {
    if (messageLength < ENCODED_LENGTH || messageLength > 0xFFFF) {
      throw new IllegalArgumentException("messageLength out of range: " + messageLength);
    }
  }

  private static void validateFlagsForEncode(int flags) {
    if ((flags & ~FLAG_RESEND) != 0) {
      throw new IllegalArgumentException("unsupported TCP header flags: " + flags);
    }
  }

  private static void validateUInt16(int value, String name) {
    if ((value & ~0xFFFF) != 0) {
      throw new IllegalArgumentException(name + " out of uint16 range: " + value);
    }
  }

  private TcpHeaderCodec() {}
}
