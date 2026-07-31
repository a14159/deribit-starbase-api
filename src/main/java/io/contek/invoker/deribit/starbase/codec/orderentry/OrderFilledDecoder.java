package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.OrderEntryTemplateDispatch;
import io.contek.invoker.deribit.starbase.codec.common.Price9Codec;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.WirePrimitives;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry schema-v11 OrderFilled (template 300). */
public final class OrderFilledDecoder {

  public static final int TEMPLATE_ID = 300;
  public static final int BLOCK_LENGTH = 16;
  public static final int GROUP_DIMENSIONS_LENGTH = 4;
  public static final int FILL_BLOCK_LENGTH = 60;
  public static final int LEG_BLOCK_LENGTH = 34;
  public static final int KNOWN_FILL_FLAGS = 0x03;

  public static void validate(ByteBuffer buffer, int offset) {
    OrderEntryTemplateDispatch.validateFrame(buffer, offset);
    requireTemplate(buffer, offset);
    int fills = fillsDimensions(offset);
    WirePrimitives.requireBounds(buffer, fills, GROUP_DIMENSIONS_LENGTH);
    int fillBlock = Short.toUnsignedInt(buffer.getShort(fills));
    int fillCount = Short.toUnsignedInt(buffer.getShort(fills + 2));
    if (fillBlock != FILL_BLOCK_LENGTH || fillCount > 2000) {
      throw new StarbaseProtocolException("invalid OrderFilled fills dimensions");
    }
    long legsLong = (long) fills + GROUP_DIMENSIONS_LENGTH + (long) fillCount * fillBlock;
    if (legsLong > Integer.MAX_VALUE) {
      throw new StarbaseProtocolException("OrderFilled fills range overflow");
    }
    int legs = (int) legsLong;
    WirePrimitives.requireBounds(buffer, legs, GROUP_DIMENSIONS_LENGTH);
    int legBlock = Short.toUnsignedInt(buffer.getShort(legs));
    int legCount = Short.toUnsignedInt(buffer.getShort(legs + 2));
    if (legBlock != LEG_BLOCK_LENGTH) {
      throw new StarbaseProtocolException("invalid OrderFilled legs dimensions");
    }
    long expectedEnd = (long) legs + GROUP_DIMENSIONS_LENGTH + (long) legCount * legBlock;
    if (expectedEnd - offset != TcpHeaderCodec.messageLength(buffer, offset)) {
      throw new StarbaseProtocolException("OrderFilled group length mismatch");
    }
    for (int index = 0; index < fillCount; index++) {
      int entry = fills + GROUP_DIMENSIONS_LENGTH + index * FILL_BLOCK_LENGTH;
      requireRequiredLong(buffer.getLong(entry), "clientOrderId");
      requireRequiredLong(buffer.getLong(entry + 8), "orderId");
      requireRequiredLong(buffer.getLong(entry + 16), "instrumentId");
      requireRequiredLong(buffer.getLong(entry + 24), "matchId");
      if (Price9Codec.isNull(buffer, entry + 32)) {
        throw new StarbaseProtocolException("null OrderFilled price");
      }
      requireDecimal(buffer, entry + 40, true, "fillQty");
      requireDecimal(buffer, entry + 49, false, "totalFilled");
      int side = buffer.get(entry + 58);
      int flags = Byte.toUnsignedInt(buffer.get(entry + 59));
      if (side != 1 && side != -1) {
        throw new StarbaseProtocolException("invalid OrderFilled side");
      }
      if ((flags & ~KNOWN_FILL_FLAGS) != 0) {
        throw new StarbaseProtocolException("invalid OrderFilled flags");
      }
    }
    for (int index = 0; index < legCount; index++) {
      int entry = legs + GROUP_DIMENSIONS_LENGTH + index * LEG_BLOCK_LENGTH;
      requireRequiredLong(buffer.getLong(entry), "leg matchId");
      requireRequiredLong(buffer.getLong(entry + 8), "leg instrumentId");
      if (Price9Codec.isNull(buffer, entry + 16)) {
        throw new StarbaseProtocolException("null OrderFilled leg price");
      }
      requireDecimal(buffer, entry + 24, true, "leg fillQty");
      int side = buffer.get(entry + 33);
      if (side != 1 && side != -1) {
        throw new StarbaseProtocolException("invalid OrderFilled leg side");
      }
    }
  }

  public static long timestampNanos(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset));
  }

  public static long execId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 8);
  }

  public static int fillCount(ByteBuffer buffer, int offset) {
    return Short.toUnsignedInt(buffer.getShort(fillsDimensions(offset) + 2));
  }

  public static long clientOrderId(ByteBuffer buffer, int offset, int index) {
    return buffer.getLong(fillEntry(buffer, offset, index));
  }

  public static long orderId(ByteBuffer buffer, int offset, int index) {
    return buffer.getLong(fillEntry(buffer, offset, index) + 8);
  }

  public static long instrumentId(ByteBuffer buffer, int offset, int index) {
    return buffer.getLong(fillEntry(buffer, offset, index) + 16);
  }

  public static long matchId(ByteBuffer buffer, int offset, int index) {
    return buffer.getLong(fillEntry(buffer, offset, index) + 24);
  }

  public static long priceMantissa(ByteBuffer buffer, int offset, int index) {
    return Price9Codec.mantissa(buffer, fillEntry(buffer, offset, index) + 32);
  }

  public static long fillQuantityMantissa(ByteBuffer buffer, int offset, int index) {
    return Decimal72Codec.mantissa(buffer, fillEntry(buffer, offset, index) + 40);
  }

  public static int fillQuantityExponent(ByteBuffer buffer, int offset, int index) {
    return Decimal72Codec.exponent(buffer, fillEntry(buffer, offset, index) + 40);
  }

  public static long totalFilledMantissa(ByteBuffer buffer, int offset, int index) {
    return Decimal72Codec.mantissa(buffer, fillEntry(buffer, offset, index) + 49);
  }

  public static int totalFilledExponent(ByteBuffer buffer, int offset, int index) {
    return Decimal72Codec.exponent(buffer, fillEntry(buffer, offset, index) + 49);
  }

  public static int side(ByteBuffer buffer, int offset, int index) {
    return buffer.get(fillEntry(buffer, offset, index) + 58);
  }

  public static int flags(ByteBuffer buffer, int offset, int index) {
    return Byte.toUnsignedInt(buffer.get(fillEntry(buffer, offset, index) + 59));
  }

  public static int legCount(ByteBuffer buffer, int offset) {
    return Short.toUnsignedInt(buffer.getShort(legsDimensions(buffer, offset) + 2));
  }

  public static long legMatchId(ByteBuffer buffer, int offset, int index) {
    return buffer.getLong(legEntry(buffer, offset, index));
  }

  public static long legInstrumentId(ByteBuffer buffer, int offset, int index) {
    return buffer.getLong(legEntry(buffer, offset, index) + 8);
  }

  public static long legPriceMantissa(ByteBuffer buffer, int offset, int index) {
    return Price9Codec.mantissa(buffer, legEntry(buffer, offset, index) + 16);
  }

  public static long legQuantityMantissa(ByteBuffer buffer, int offset, int index) {
    return Decimal72Codec.mantissa(buffer, legEntry(buffer, offset, index) + 24);
  }

  public static int legQuantityExponent(ByteBuffer buffer, int offset, int index) {
    return Decimal72Codec.exponent(buffer, legEntry(buffer, offset, index) + 24);
  }

  public static int legSide(ByteBuffer buffer, int offset, int index) {
    return buffer.get(legEntry(buffer, offset, index) + 33);
  }

  private static int fillEntry(ByteBuffer buffer, int offset, int index) {
    int count = fillCount(buffer, offset);
    requireIndex(index, count, "fill");
    return fillsDimensions(offset) + GROUP_DIMENSIONS_LENGTH + index * FILL_BLOCK_LENGTH;
  }

  private static int legEntry(ByteBuffer buffer, int offset, int index) {
    int count = legCount(buffer, offset);
    requireIndex(index, count, "leg");
    return legsDimensions(buffer, offset) + GROUP_DIMENSIONS_LENGTH + index * LEG_BLOCK_LENGTH;
  }

  private static int legsDimensions(ByteBuffer buffer, int offset) {
    return fillsDimensions(offset)
        + GROUP_DIMENSIONS_LENGTH
        + fillCount(buffer, offset) * FILL_BLOCK_LENGTH;
  }

  private static int fillsDimensions(int offset) {
    return body(offset) + BLOCK_LENGTH;
  }

  private static int body(int offset) {
    return offset + SessionCodecSupport.BODY_OFFSET;
  }

  private static void requireTemplate(ByteBuffer buffer, int offset) {
    int actual = TcpHeaderCodec.messageTypeId(buffer, offset);
    if (actual != TEMPLATE_ID) {
      throw new StarbaseProtocolException(
          "expected OrderFilled template " + TEMPLATE_ID + " but received " + actual);
    }
  }

  private static void requireRequiredLong(long value, String field) {
    if (value == Long.MIN_VALUE) {
      throw new StarbaseProtocolException("null OrderFilled " + field);
    }
  }

  private static void requireDecimal(
      ByteBuffer buffer, int offset, boolean positive, String field) {
    long mantissa = Decimal72Codec.mantissa(buffer, offset);
    int exponent = Decimal72Codec.exponent(buffer, offset);
    if (mantissa == Decimal72Codec.NULL_MANTISSA
        || exponent == Decimal72Codec.NULL_EXPONENT
        || positive && mantissa <= 0
        || !positive && mantissa < 0) {
      throw new StarbaseProtocolException("invalid OrderFilled " + field);
    }
  }

  private static void requireIndex(int index, int count, String group) {
    if (index < 0 || index >= count) {
      throw new IndexOutOfBoundsException(group + " index " + index + ", count " + count);
    }
  }

  private OrderFilledDecoder() {}
}
