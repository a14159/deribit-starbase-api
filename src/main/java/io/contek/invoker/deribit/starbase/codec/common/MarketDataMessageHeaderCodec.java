package io.contek.invoker.deribit.starbase.codec.common;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded 16-byte market-data message header decoder (schema 2102 v1). */
public final class MarketDataMessageHeaderCodec {

  public static final int ENCODED_LENGTH = 16;

  public static final int MESSAGE_LENGTH_OFFSET = 0;
  public static final int TEMPLATE_ID_OFFSET = 2;
  public static final int VERSION_OFFSET = 4;
  public static final int FLAGS_OFFSET = 6;
  public static final int TRANSACT_TIME_NANOS_OFFSET = 8;

  public static final int FLAG_START_OF_TRANSACTION = 1;
  public static final int FLAG_END_OF_TRANSACTION = 2;
  private static final int KNOWN_FLAGS =
      FLAG_START_OF_TRANSACTION | FLAG_END_OF_TRANSACTION;

  /** Validates the header and the complete message range advertised by messageLength. */
  public static void validate(ByteBuffer buffer, int messageOffset) {
    requireHeader(buffer, messageOffset);
    int length = messageLengthUnchecked(buffer, messageOffset);
    if (length < ENCODED_LENGTH) {
      throw new StarbaseProtocolException("invalid market-data messageLength: " + length);
    }
    int messageFlags = flagsUnchecked(buffer, messageOffset);
    if ((messageFlags & ~KNOWN_FLAGS) != 0) {
      throw new StarbaseProtocolException(
          "unsupported market-data transaction flags: " + messageFlags);
    }
    WirePrimitives.requireBounds(buffer, messageOffset, length);
  }

  public static int messageLength(ByteBuffer buffer, int messageOffset) {
    requireHeader(buffer, messageOffset);
    return messageLengthUnchecked(buffer, messageOffset);
  }

  public static int templateId(ByteBuffer buffer, int messageOffset) {
    requireHeader(buffer, messageOffset);
    return Short.toUnsignedInt(buffer.getShort(messageOffset + TEMPLATE_ID_OFFSET));
  }

  public static int version(ByteBuffer buffer, int messageOffset) {
    requireHeader(buffer, messageOffset);
    return Short.toUnsignedInt(buffer.getShort(messageOffset + VERSION_OFFSET));
  }

  public static int flags(ByteBuffer buffer, int messageOffset) {
    requireHeader(buffer, messageOffset);
    return flagsUnchecked(buffer, messageOffset);
  }

  public static boolean isStartOfTransaction(ByteBuffer buffer, int messageOffset) {
    return (flags(buffer, messageOffset) & FLAG_START_OF_TRANSACTION) != 0;
  }

  public static boolean isEndOfTransaction(ByteBuffer buffer, int messageOffset) {
    return (flags(buffer, messageOffset) & FLAG_END_OF_TRANSACTION) != 0;
  }

  public static long transactTimeNanos(ByteBuffer buffer, int messageOffset) {
    requireHeader(buffer, messageOffset);
    return buffer.getLong(messageOffset + TRANSACT_TIME_NANOS_OFFSET);
  }

  private static int messageLengthUnchecked(ByteBuffer buffer, int messageOffset) {
    return Short.toUnsignedInt(buffer.getShort(messageOffset + MESSAGE_LENGTH_OFFSET));
  }

  private static int flagsUnchecked(ByteBuffer buffer, int messageOffset) {
    return Short.toUnsignedInt(buffer.getShort(messageOffset + FLAGS_OFFSET));
  }

  private static void requireHeader(ByteBuffer buffer, int messageOffset) {
    WirePrimitives.requireLittleEndian(buffer);
    WirePrimitives.requireBounds(buffer, messageOffset, ENCODED_LENGTH);
  }

  private MarketDataMessageHeaderCodec() {}
}
