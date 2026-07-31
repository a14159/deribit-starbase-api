package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.OrderEntryTemplateDispatch;
import io.contek.invoker.deribit.starbase.codec.common.Price9Codec;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.WirePrimitives;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry schema-v11 OrderPlaced (template 312). */
public final class OrderPlacedDecoder {

  public static final int TEMPLATE_ID = 312;
  public static final int BLOCK_LENGTH = 88;
  public static final int GROUP_DIMENSIONS_LENGTH = 4;
  public static final int FILL_BLOCK_LENGTH = 25;
  public static final int LEG_BLOCK_LENGTH = 34;

  public static void validate(ByteBuffer buffer, int offset) {
    OrderEntryTemplateDispatch.validateFrame(buffer, offset);
    requireTemplate(buffer, offset);
    validateFixed(buffer, offset);
    int fills = fillsDimensions(offset);
    WirePrimitives.requireBounds(buffer, fills, GROUP_DIMENSIONS_LENGTH);
    int fillBlock = Short.toUnsignedInt(buffer.getShort(fills));
    int fillCount = Short.toUnsignedInt(buffer.getShort(fills + 2));
    if (fillBlock != FILL_BLOCK_LENGTH || fillCount > 2000) {
      throw new StarbaseProtocolException("invalid OrderPlaced fills dimensions");
    }
    long legsLong = (long) fills + GROUP_DIMENSIONS_LENGTH + (long) fillCount * fillBlock;
    if (legsLong > Integer.MAX_VALUE) {
      throw new StarbaseProtocolException("OrderPlaced fills range overflow");
    }
    int legs = (int) legsLong;
    WirePrimitives.requireBounds(buffer, legs, GROUP_DIMENSIONS_LENGTH);
    int legBlock = Short.toUnsignedInt(buffer.getShort(legs));
    int legCount = Short.toUnsignedInt(buffer.getShort(legs + 2));
    if (legBlock != LEG_BLOCK_LENGTH) {
      throw new StarbaseProtocolException("invalid OrderPlaced legs dimensions");
    }
    long expectedEnd = (long) legs + GROUP_DIMENSIONS_LENGTH + (long) legCount * legBlock;
    if (expectedEnd - offset != TcpHeaderCodec.messageLength(buffer, offset)) {
      throw new StarbaseProtocolException("OrderPlaced group length mismatch");
    }
    for (int index = 0; index < fillCount; index++) {
      int entry = fills + GROUP_DIMENSIONS_LENGTH + index * FILL_BLOCK_LENGTH;
      requireRequiredLong(buffer.getLong(entry), "fill matchId");
      requirePrice(buffer, entry + 8, "fill price");
      requireDecimal(buffer, entry + 16, true, "fill quantity");
    }
    for (int index = 0; index < legCount; index++) {
      int entry = legs + GROUP_DIMENSIONS_LENGTH + index * LEG_BLOCK_LENGTH;
      requireRequiredLong(buffer.getLong(entry), "leg matchId");
      requireRequiredLong(buffer.getLong(entry + 8), "leg instrumentId");
      requirePrice(buffer, entry + 16, "leg price");
      requireDecimal(buffer, entry + 24, true, "leg quantity");
      int side = buffer.get(entry + 33);
      if (side != 1 && side != -1) {
        throw new StarbaseProtocolException("invalid OrderPlaced leg side");
      }
    }
  }

  public static long timestampNanos(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset));
  }

  public static long execId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 8);
  }

  public static long clientOrderId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 16);
  }

  public static long orderId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 24);
  }

  public static long instrumentId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 32);
  }

  public static long priceMantissa(ByteBuffer buffer, int offset) {
    return Price9Codec.mantissa(buffer, body(offset) + 40);
  }

  public static long quantityMantissa(ByteBuffer buffer, int offset) {
    return Decimal72Codec.mantissa(buffer, body(offset) + 48);
  }

  public static int quantityExponent(ByteBuffer buffer, int offset) {
    return Decimal72Codec.exponent(buffer, body(offset) + 48);
  }

  public static long totalFilledMantissa(ByteBuffer buffer, int offset) {
    return Decimal72Codec.mantissa(buffer, body(offset) + 57);
  }

  public static int totalFilledExponent(ByteBuffer buffer, int offset) {
    return Decimal72Codec.exponent(buffer, body(offset) + 57);
  }

  public static long visibleQuantityMantissa(ByteBuffer buffer, int offset) {
    return Decimal72Codec.mantissa(buffer, body(offset) + 66);
  }

  public static int visibleQuantityExponent(ByteBuffer buffer, int offset) {
    return Decimal72Codec.exponent(buffer, body(offset) + 66);
  }

  public static int status(ByteBuffer buffer, int offset) {
    return buffer.get(body(offset) + 75);
  }

  public static int cancelReason(ByteBuffer buffer, int offset) {
    return buffer.get(body(offset) + 76);
  }

  public static long correlationId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 80);
  }

  public static int fillCount(ByteBuffer buffer, int offset) {
    return Short.toUnsignedInt(buffer.getShort(fillsDimensions(offset) + 2));
  }

  public static long fillMatchId(ByteBuffer buffer, int offset, int index) {
    return buffer.getLong(fillEntry(buffer, offset, index));
  }

  public static long fillPriceMantissa(ByteBuffer buffer, int offset, int index) {
    return Price9Codec.mantissa(buffer, fillEntry(buffer, offset, index) + 8);
  }

  public static long fillQuantityMantissa(ByteBuffer buffer, int offset, int index) {
    return Decimal72Codec.mantissa(buffer, fillEntry(buffer, offset, index) + 16);
  }

  public static int fillQuantityExponent(ByteBuffer buffer, int offset, int index) {
    return Decimal72Codec.exponent(buffer, fillEntry(buffer, offset, index) + 16);
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

  private static void validateFixed(ByteBuffer buffer, int offset) {
    WirePrimitives.requireBounds(buffer, body(offset), BLOCK_LENGTH);
    requireRequiredLong(clientOrderId(buffer, offset), "clientOrderId");
    requireRequiredLong(orderId(buffer, offset), "orderId");
    requireRequiredLong(instrumentId(buffer, offset), "instrumentId");
    requirePrice(buffer, body(offset) + 40, "price");
    requireDecimal(buffer, body(offset) + 48, true, "quantity");
    requireDecimal(buffer, body(offset) + 57, false, "totalFilled");
    requireDecimal(buffer, body(offset) + 66, false, "visible quantity");
    int status = status(buffer, offset);
    int reason = cancelReason(buffer, offset);
    if (status < 1 || status > 4) {
      throw new StarbaseProtocolException("invalid OrderPlaced status");
    }
    if (reason < 0 || reason > 17) {
      throw new StarbaseProtocolException("invalid OrderPlaced cancel reason");
    }
    for (int index = 77; index < 80; index++) {
      if (buffer.get(body(offset) + index) != 0) {
        throw new StarbaseProtocolException("non-zero OrderPlaced fixed padding");
      }
    }
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
          "expected OrderPlaced template " + TEMPLATE_ID + " but received " + actual);
    }
  }

  private static void requireRequiredLong(long value, String field) {
    if (value == Long.MIN_VALUE) {
      throw new StarbaseProtocolException("null OrderPlaced " + field);
    }
  }

  private static void requirePrice(ByteBuffer buffer, int offset, String field) {
    if (Price9Codec.isNull(buffer, offset)) {
      throw new StarbaseProtocolException("null OrderPlaced " + field);
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
      throw new StarbaseProtocolException("invalid OrderPlaced " + field);
    }
  }

  private static void requireIndex(int index, int count, String group) {
    if (index < 0 || index >= count) {
      throw new IndexOutOfBoundsException(group + " index " + index + ", count " + count);
    }
  }

  private OrderPlacedDecoder() {}
}
