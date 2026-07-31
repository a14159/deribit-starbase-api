package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.MarketDataTemplateDispatch;
import io.contek.invoker.deribit.starbase.codec.common.Price9Codec;
import io.contek.invoker.deribit.starbase.codec.common.WirePrimitives;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Decoder for market-data template 10, including its two repeating groups. */
public final class InstrumentDefinitionDecoder {

  public static final int TEMPLATE_ID = 10;
  public static final int BLOCK_LENGTH = 260;
  public static final int GROUP_DIMENSIONS_LENGTH = 4;
  public static final int LARGE_TICK_SIZE_BLOCK_LENGTH = 16;
  public static final int LEG_BLOCK_LENGTH = 9;

  public static final int INSTRUMENT_ID_OFFSET = 0;
  public static final int NAME_OFFSET = 8;
  public static final int NAME_LENGTH = 128;
  public static final int INDEX_ID_OFFSET = 136;
  public static final int UNDERLYING_OFFSET = 144;
  public static final int UNDERLYING_LENGTH = 64;
  public static final int QUANTITY_ASSET_OFFSET = 208;
  public static final int QUANTITY_ASSET_LENGTH = 8;
  public static final int PRICE_ASSET_OFFSET = 216;
  public static final int PRICE_ASSET_LENGTH = 8;
  public static final int EXPIRY_TIME_OFFSET = 224;
  public static final int STRIKE_PRICE_OFFSET = 232;
  public static final int MIN_ORDER_QUANTITY_OFFSET = 240;
  public static final int TICK_SIZE_OFFSET = 248;
  public static final int QUANTITY_EXPONENT_OFFSET = 256;
  public static final int INSTRUMENT_TYPE_OFFSET = 257;
  public static final int INSTRUMENT_FLAGS_OFFSET = 258;
  public static final int INSTRUMENT_STATUS_OFFSET = 259;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    int actualTemplate = MarketDataTemplateDispatch.validateMessage(buffer, messageOffset);
    if (actualTemplate != TEMPLATE_ID) {
      throw new StarbaseProtocolException(
          "expected market-data template " + TEMPLATE_ID + " but received " + actualTemplate);
    }
    int messageLength = MarketDataMessageHeaderCodec.messageLength(buffer, messageOffset);
    int largeDimensions = body(messageOffset) + BLOCK_LENGTH;
    WirePrimitives.requireBounds(buffer, largeDimensions, GROUP_DIMENSIONS_LENGTH);
    int largeBlockLength = Short.toUnsignedInt(buffer.getShort(largeDimensions));
    int largeCount = Short.toUnsignedInt(buffer.getShort(largeDimensions + 2));
    if (largeBlockLength != LARGE_TICK_SIZE_BLOCK_LENGTH) {
      throw new StarbaseProtocolException(
          "invalid largeTickSizes blockLength: " + largeBlockLength);
    }
    long legDimensionsLong =
        (long) largeDimensions + GROUP_DIMENSIONS_LENGTH + (long) largeCount * largeBlockLength;
    if (legDimensionsLong > Integer.MAX_VALUE) {
      throw new StarbaseProtocolException("largeTickSizes range overflow");
    }
    int legDimensions = (int) legDimensionsLong;
    WirePrimitives.requireBounds(buffer, legDimensions, GROUP_DIMENSIONS_LENGTH);
    int legBlockLength = Short.toUnsignedInt(buffer.getShort(legDimensions));
    int legCount = Short.toUnsignedInt(buffer.getShort(legDimensions + 2));
    if (legBlockLength != LEG_BLOCK_LENGTH) {
      throw new StarbaseProtocolException("invalid legs blockLength: " + legBlockLength);
    }
    long expectedEnd =
        (long) legDimensions + GROUP_DIMENSIONS_LENGTH + (long) legCount * legBlockLength;
    int unpaddedLength = Math.toIntExact(expectedEnd - messageOffset);
    MarketDataDecoderSupport.validateEncodedLength(
        buffer, messageOffset, TEMPLATE_ID, unpaddedLength);
    validateFixedValues(buffer, messageOffset);
    for (int index = 0; index < largeCount; index++) {
      int entry =
          largeDimensions
              + GROUP_DIMENSIONS_LENGTH
              + index * LARGE_TICK_SIZE_BLOCK_LENGTH;
      requireNonNullLong(buffer.getLong(entry), "largeTickSize");
      requireNonNullLong(buffer.getLong(entry + Long.BYTES), "thresholdPrice");
    }
    for (int index = 0; index < legCount; index++) {
      int entry = legDimensions + GROUP_DIMENSIONS_LENGTH + index * LEG_BLOCK_LENGTH;
      requireNonNullLong(buffer.getLong(entry), "legInstrumentId");
      if (buffer.get(entry + Long.BYTES) == Byte.MIN_VALUE) {
        throw new StarbaseProtocolException("null leg ratio");
      }
    }
  }

  public static long instrumentId(ByteBuffer buffer, int messageOffset) {
    requireFixed(buffer, messageOffset);
    return buffer.getLong(body(messageOffset) + INSTRUMENT_ID_OFFSET);
  }

  public static int nameLength(ByteBuffer buffer, int messageOffset) {
    return MarketDataDecoderSupport.fixedAsciiLength(
        buffer, messageOffset, NAME_OFFSET, NAME_LENGTH);
  }

  public static int nameByte(ByteBuffer buffer, int messageOffset, int index) {
    return MarketDataDecoderSupport.fixedAsciiByte(
        buffer, messageOffset, NAME_OFFSET, NAME_LENGTH, index);
  }

  public static long indexId(ByteBuffer buffer, int messageOffset) {
    requireFixed(buffer, messageOffset);
    return buffer.getLong(body(messageOffset) + INDEX_ID_OFFSET);
  }

  public static int underlyingLength(ByteBuffer buffer, int messageOffset) {
    return MarketDataDecoderSupport.fixedAsciiLength(
        buffer, messageOffset, UNDERLYING_OFFSET, UNDERLYING_LENGTH);
  }

  public static int underlyingByte(ByteBuffer buffer, int messageOffset, int index) {
    return MarketDataDecoderSupport.fixedAsciiByte(
        buffer, messageOffset, UNDERLYING_OFFSET, UNDERLYING_LENGTH, index);
  }

  public static int quantityAssetLength(ByteBuffer buffer, int messageOffset) {
    return MarketDataDecoderSupport.fixedAsciiLength(
        buffer, messageOffset, QUANTITY_ASSET_OFFSET, QUANTITY_ASSET_LENGTH);
  }

  public static int quantityAssetByte(ByteBuffer buffer, int messageOffset, int index) {
    return MarketDataDecoderSupport.fixedAsciiByte(
        buffer, messageOffset, QUANTITY_ASSET_OFFSET, QUANTITY_ASSET_LENGTH, index);
  }

  public static int priceAssetLength(ByteBuffer buffer, int messageOffset) {
    return MarketDataDecoderSupport.fixedAsciiLength(
        buffer, messageOffset, PRICE_ASSET_OFFSET, PRICE_ASSET_LENGTH);
  }

  public static int priceAssetByte(ByteBuffer buffer, int messageOffset, int index) {
    return MarketDataDecoderSupport.fixedAsciiByte(
        buffer, messageOffset, PRICE_ASSET_OFFSET, PRICE_ASSET_LENGTH, index);
  }

  public static long expiryTimeNanos(ByteBuffer buffer, int messageOffset) {
    requireFixed(buffer, messageOffset);
    return buffer.getLong(body(messageOffset) + EXPIRY_TIME_OFFSET);
  }

  public static boolean isExpiryTimeNull(ByteBuffer buffer, int messageOffset) {
    return expiryTimeNanos(buffer, messageOffset) == Long.MIN_VALUE;
  }

  public static long strikePriceMantissa(ByteBuffer buffer, int messageOffset) {
    requireFixed(buffer, messageOffset);
    return buffer.getLong(body(messageOffset) + STRIKE_PRICE_OFFSET);
  }

  public static boolean isStrikePriceNull(ByteBuffer buffer, int messageOffset) {
    return strikePriceMantissa(buffer, messageOffset) == Price9Codec.NULL_MANTISSA;
  }

  public static long minOrderQuantityMantissa(ByteBuffer buffer, int messageOffset) {
    requireFixed(buffer, messageOffset);
    return buffer.getLong(body(messageOffset) + MIN_ORDER_QUANTITY_OFFSET);
  }

  public static long tickSizeMantissa(ByteBuffer buffer, int messageOffset) {
    requireFixed(buffer, messageOffset);
    return buffer.getLong(body(messageOffset) + TICK_SIZE_OFFSET);
  }

  public static int quantityExponent(ByteBuffer buffer, int messageOffset) {
    requireFixed(buffer, messageOffset);
    return buffer.get(body(messageOffset) + QUANTITY_EXPONENT_OFFSET);
  }

  public static int instrumentType(ByteBuffer buffer, int messageOffset) {
    requireFixed(buffer, messageOffset);
    return buffer.get(body(messageOffset) + INSTRUMENT_TYPE_OFFSET);
  }

  public static int instrumentFlags(ByteBuffer buffer, int messageOffset) {
    requireFixed(buffer, messageOffset);
    return Byte.toUnsignedInt(buffer.get(body(messageOffset) + INSTRUMENT_FLAGS_OFFSET));
  }

  public static int instrumentStatus(ByteBuffer buffer, int messageOffset) {
    requireFixed(buffer, messageOffset);
    return buffer.get(body(messageOffset) + INSTRUMENT_STATUS_OFFSET);
  }

  public static int largeTickSizeCount(ByteBuffer buffer, int messageOffset) {
    requireLargeDimensions(buffer, messageOffset);
    return Short.toUnsignedInt(buffer.getShort(largeDimensions(messageOffset) + 2));
  }

  public static long largeTickSizeMantissa(
      ByteBuffer buffer, int messageOffset, int index) {
    int entry = largeEntry(buffer, messageOffset, index);
    return buffer.getLong(entry);
  }

  public static long largeTickThresholdMantissa(
      ByteBuffer buffer, int messageOffset, int index) {
    int entry = largeEntry(buffer, messageOffset, index);
    return buffer.getLong(entry + Long.BYTES);
  }

  public static int legCount(ByteBuffer buffer, int messageOffset) {
    int dimensions = legDimensions(buffer, messageOffset);
    WirePrimitives.requireBounds(buffer, dimensions, GROUP_DIMENSIONS_LENGTH);
    return Short.toUnsignedInt(buffer.getShort(dimensions + 2));
  }

  public static long legInstrumentId(ByteBuffer buffer, int messageOffset, int index) {
    return buffer.getLong(legEntry(buffer, messageOffset, index));
  }

  public static int legRatio(ByteBuffer buffer, int messageOffset, int index) {
    return buffer.get(legEntry(buffer, messageOffset, index) + Long.BYTES);
  }

  private static int largeEntry(ByteBuffer buffer, int messageOffset, int index) {
    int count = largeTickSizeCount(buffer, messageOffset);
    requireIndex(index, count, "largeTickSizes");
    int entry = largeDimensions(messageOffset) + GROUP_DIMENSIONS_LENGTH + index * LARGE_TICK_SIZE_BLOCK_LENGTH;
    WirePrimitives.requireBounds(buffer, entry, LARGE_TICK_SIZE_BLOCK_LENGTH);
    return entry;
  }

  private static int legEntry(ByteBuffer buffer, int messageOffset, int index) {
    int dimensions = legDimensions(buffer, messageOffset);
    WirePrimitives.requireBounds(buffer, dimensions, GROUP_DIMENSIONS_LENGTH);
    int count = Short.toUnsignedInt(buffer.getShort(dimensions + 2));
    requireIndex(index, count, "legs");
    int entry = dimensions + GROUP_DIMENSIONS_LENGTH + index * LEG_BLOCK_LENGTH;
    WirePrimitives.requireBounds(buffer, entry, LEG_BLOCK_LENGTH);
    return entry;
  }

  private static void requireLargeDimensions(ByteBuffer buffer, int messageOffset) {
    requireFixed(buffer, messageOffset);
    WirePrimitives.requireBounds(
        buffer, largeDimensions(messageOffset), GROUP_DIMENSIONS_LENGTH);
  }

  private static int legDimensions(ByteBuffer buffer, int messageOffset) {
    int count = largeTickSizeCount(buffer, messageOffset);
    return largeDimensions(messageOffset)
        + GROUP_DIMENSIONS_LENGTH
        + count * LARGE_TICK_SIZE_BLOCK_LENGTH;
  }

  private static int largeDimensions(int messageOffset) {
    return body(messageOffset) + BLOCK_LENGTH;
  }

  private static int body(int messageOffset) {
    return messageOffset + MarketDataDecoderSupport.BODY_OFFSET;
  }

  private static void requireFixed(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
  }

  private static void requireIndex(int index, int count, String group) {
    if (index < 0 || index >= count) {
      throw new IndexOutOfBoundsException(group + " index " + index + ", count " + count);
    }
  }

  private static void validateFixedValues(ByteBuffer buffer, int messageOffset) {
    int body = body(messageOffset);
    requireNonNullLong(buffer.getLong(body + INSTRUMENT_ID_OFFSET), "instrumentId");
    requireNonNullLong(buffer.getLong(body + INDEX_ID_OFFSET), "indexId");
    requireNonNullLong(
        buffer.getLong(body + MIN_ORDER_QUANTITY_OFFSET), "minOrderQuantity");
    requireNonNullLong(buffer.getLong(body + TICK_SIZE_OFFSET), "tickSize");
    int exponent = buffer.get(body + QUANTITY_EXPONENT_OFFSET);
    if (exponent == Byte.MIN_VALUE) {
      throw new StarbaseProtocolException("null quantityExponent");
    }
    int type = buffer.get(body + INSTRUMENT_TYPE_OFFSET);
    if (type < 0 || type > 5) {
      throw new StarbaseProtocolException("unknown InstrumentType: " + type);
    }
    int flags = Byte.toUnsignedInt(buffer.get(body + INSTRUMENT_FLAGS_OFFSET));
    if ((flags & ~0x07) != 0) {
      throw new StarbaseProtocolException("unknown InstrumentFlags bits: " + flags);
    }
    validateStatus(buffer.get(body + INSTRUMENT_STATUS_OFFSET));
  }

  static void validateStatus(int status) {
    if (status < 0 || status > 5) {
      throw new StarbaseProtocolException("unknown InstrumentStatus: " + status);
    }
  }

  private static void requireNonNullLong(long value, String field) {
    if (value == Long.MIN_VALUE) {
      throw new StarbaseProtocolException("null required InstrumentDefinition " + field);
    }
  }

  private InstrumentDefinitionDecoder() {}
}
