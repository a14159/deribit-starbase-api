package io.contek.invoker.deribit.starbase.codec.common;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded 24-byte market-data UDP packet header codec (schema 2102 v1). */
public final class UdpPacketHeaderCodec {

  public static final int ENCODED_LENGTH = 24;

  public static final int SENDING_TIME_NANOS_OFFSET = 0;
  public static final int SEQUENCE_NUMBER_OFFSET = 8;
  public static final int CHANNEL_ID_OFFSET = 16;
  public static final int TYPE_OFFSET = 20;
  public static final int MESSAGE_COUNT_OFFSET = 22;

  public static final int TYPE_INCREMENTAL_UPDATE = 1;
  public static final int TYPE_SNAPSHOT = 2;
  public static final int TYPE_RETRANSMIT = 4;
  public static final int TYPE_CONTROL = 0;
  public static final int TYPE_RETRANSMIT_SUCCESS =
      TYPE_INCREMENTAL_UPDATE | TYPE_RETRANSMIT;

  public static void encode(
      ByteBuffer buffer,
      int headerOffset,
      long sendingTimeNanos,
      long sequenceNumber,
      int channelId,
      int type,
      int messageCount) {
    validateTypeForEncode(type);
    validateUInt16(messageCount, "messageCount");
    requireHeader(buffer, headerOffset);
    buffer.putLong(headerOffset + SENDING_TIME_NANOS_OFFSET, sendingTimeNanos);
    buffer.putLong(headerOffset + SEQUENCE_NUMBER_OFFSET, sequenceNumber);
    buffer.putInt(headerOffset + CHANNEL_ID_OFFSET, channelId);
    buffer.putShort(headerOffset + TYPE_OFFSET, (short) type);
    buffer.putShort(headerOffset + MESSAGE_COUNT_OFFSET, (short) messageCount);
  }

  public static void validate(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    int packetType = typeUnchecked(buffer, headerOffset);
    if (!isValidType(packetType)) {
      throw new StarbaseProtocolException("invalid UDP packet type: " + packetType);
    }
  }

  public static long sendingTimeNanos(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return buffer.getLong(headerOffset + SENDING_TIME_NANOS_OFFSET);
  }

  public static long sequenceNumber(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return buffer.getLong(headerOffset + SEQUENCE_NUMBER_OFFSET);
  }

  public static int channelId(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return buffer.getInt(headerOffset + CHANNEL_ID_OFFSET);
  }

  public static int type(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return typeUnchecked(buffer, headerOffset);
  }

  public static int messageCount(ByteBuffer buffer, int headerOffset) {
    requireHeader(buffer, headerOffset);
    return Short.toUnsignedInt(buffer.getShort(headerOffset + MESSAGE_COUNT_OFFSET));
  }

  private static int typeUnchecked(ByteBuffer buffer, int headerOffset) {
    return Short.toUnsignedInt(buffer.getShort(headerOffset + TYPE_OFFSET));
  }

  private static void requireHeader(ByteBuffer buffer, int headerOffset) {
    WirePrimitives.requireLittleEndian(buffer);
    WirePrimitives.requireBounds(buffer, headerOffset, ENCODED_LENGTH);
  }

  private static boolean isValidType(int type) {
    return type == TYPE_CONTROL
        || type == TYPE_INCREMENTAL_UPDATE
        || type == TYPE_SNAPSHOT
        || type == TYPE_RETRANSMIT
        || type == TYPE_RETRANSMIT_SUCCESS;
  }

  private static void validateTypeForEncode(int type) {
    if (!isValidType(type)) {
      throw new IllegalArgumentException("invalid UDP packet type: " + type);
    }
  }

  private static void validateUInt16(int value, String name) {
    if ((value & ~0xFFFF) != 0) {
      throw new IllegalArgumentException(name + " out of uint16 range: " + value);
    }
  }

  private UdpPacketHeaderCodec() {}
}
