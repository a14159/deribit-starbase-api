package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.OrderEntryTemplateDispatch;
import io.contek.invoker.deribit.starbase.codec.common.Price9Codec;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.WirePrimitives;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry schema-v11 NewOrderResponse (template 200). */
public final class NewOrderResponseDecoder {

  public static final int TEMPLATE_ID = 200;
  public static final int BLOCK_LENGTH = 94;
  public static final int GROUP_DIMENSIONS_LENGTH = 4;
  public static final int FILL_BLOCK_LENGTH = 25;
  public static final int LEG_BLOCK_LENGTH = 34;

  public static void validate(ByteBuffer buffer, int offset) {
    OrderEntryTemplateDispatch.validateFrame(buffer, offset);
    requireTemplate(buffer, offset);
    int messageLength = TcpHeaderCodec.messageLength(buffer, offset);
    int fills = fillsDimensions(offset);
    WirePrimitives.requireBounds(buffer, fills, GROUP_DIMENSIONS_LENGTH);
    int fillBlock = Short.toUnsignedInt(buffer.getShort(fills));
    int fillCount = Short.toUnsignedInt(buffer.getShort(fills + 2));
    if (fillBlock != FILL_BLOCK_LENGTH || fillCount > 2000) {
      throw new StarbaseProtocolException("invalid NewOrderResponse fills dimensions");
    }
    long legsLong = (long) fills + GROUP_DIMENSIONS_LENGTH + (long) fillCount * fillBlock;
    if (legsLong > Integer.MAX_VALUE) {
      throw new StarbaseProtocolException("NewOrderResponse fills range overflow");
    }
    int legs = (int) legsLong;
    WirePrimitives.requireBounds(buffer, legs, GROUP_DIMENSIONS_LENGTH);
    int legBlock = Short.toUnsignedInt(buffer.getShort(legs));
    int legCount = Short.toUnsignedInt(buffer.getShort(legs + 2));
    if (legBlock != LEG_BLOCK_LENGTH) {
      throw new StarbaseProtocolException("invalid NewOrderResponse legs dimensions");
    }
    long expectedEnd =
        (long) legs + GROUP_DIMENSIONS_LENGTH + (long) legCount * legBlock;
    if (expectedEnd - offset != messageLength) {
      throw new StarbaseProtocolException("NewOrderResponse group length mismatch");
    }
    validateFixed(buffer, offset);
    for (int index = 0; index < fillCount; index++) {
      int entry = fills + GROUP_DIMENSIONS_LENGTH + index * FILL_BLOCK_LENGTH;
      requireRequiredPrice(buffer, entry + 8, "fillPrice");
      requireRequiredDecimal(buffer, entry + 16, true, "fillQty");
    }
    for (int index = 0; index < legCount; index++) {
      int entry = legs + GROUP_DIMENSIONS_LENGTH + index * LEG_BLOCK_LENGTH;
      requireRequiredPrice(buffer, entry + 16, "legPrice");
      requireRequiredDecimal(buffer, entry + 24, true, "legQty");
      int side = buffer.get(entry + 33);
      if (side != 1 && side != -1) {
        throw new StarbaseProtocolException("invalid NewOrderResponse leg side");
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

  public static long correlationId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 24);
  }

  public static long orderId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 32);
  }

  public static long instrumentId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 40);
  }

  public static long priceMantissa(ByteBuffer buffer, int offset) {
    return Price9Codec.mantissa(buffer, body(offset) + 48);
  }

  public static long quantityMantissa(ByteBuffer buffer, int offset) {
    return Decimal72Codec.mantissa(buffer, body(offset) + 56);
  }

  public static int quantityExponent(ByteBuffer buffer, int offset) {
    return Decimal72Codec.exponent(buffer, body(offset) + 56);
  }

  public static long totalFilledMantissa(ByteBuffer buffer, int offset) {
    return Decimal72Codec.mantissa(buffer, body(offset) + 65);
  }

  public static int totalFilledExponent(ByteBuffer buffer, int offset) {
    return Decimal72Codec.exponent(buffer, body(offset) + 65);
  }

  public static long visibleQuantityMantissa(ByteBuffer buffer, int offset) {
    return Decimal72Codec.mantissa(buffer, body(offset) + 74);
  }

  public static int visibleQuantityExponent(ByteBuffer buffer, int offset) {
    return Decimal72Codec.exponent(buffer, body(offset) + 74);
  }

  public static long receiveTimeNanos(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 83);
  }

  public static int side(ByteBuffer buffer, int offset) {
    return buffer.get(body(offset) + 91);
  }

  public static int status(ByteBuffer buffer, int offset) {
    return buffer.get(body(offset) + 92);
  }

  public static int cancelReason(ByteBuffer buffer, int offset) {
    return buffer.get(body(offset) + 93);
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
    requireRequiredDecimal(buffer, body(offset) + 56, true, "quantity");
    requireRequiredDecimal(buffer, body(offset) + 65, false, "totalFilled");
    requireRequiredDecimal(buffer, body(offset) + 74, false, "visibleQty");
    int side = side(buffer, offset);
    int status = status(buffer, offset);
    int reason = cancelReason(buffer, offset);
    if (side != 1 && side != -1) {
      throw new StarbaseProtocolException("invalid NewOrderResponse side");
    }
    if (status < 1 || status > 4) {
      throw new StarbaseProtocolException("invalid NewOrderResponse status");
    }
    if (reason < 0 || reason > 17) {
      throw new StarbaseProtocolException("invalid NewOrderResponse cancel reason");
    }
  }

  private static void requireRequiredPrice(ByteBuffer buffer, int offset, String field) {
    if (Price9Codec.isNull(buffer, offset)) {
      throw new StarbaseProtocolException("null NewOrderResponse " + field);
    }
  }

  private static void requireRequiredDecimal(
      ByteBuffer buffer, int offset, boolean positive, String field) {
    long mantissa = Decimal72Codec.mantissa(buffer, offset);
    int exponent = Decimal72Codec.exponent(buffer, offset);
    if (mantissa == Decimal72Codec.NULL_MANTISSA
        || exponent == Decimal72Codec.NULL_EXPONENT
        || positive && mantissa <= 0
        || !positive && mantissa < 0) {
      throw new StarbaseProtocolException("invalid NewOrderResponse " + field);
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
    return fillsDimensions(offset) + GROUP_DIMENSIONS_LENGTH + fillCount(buffer, offset) * FILL_BLOCK_LENGTH;
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
          "expected NewOrderResponse template " + TEMPLATE_ID + " but received " + actual);
    }
  }

  private static void requireIndex(int index, int count, String group) {
    if (index < 0 || index >= count) {
      throw new IndexOutOfBoundsException(group + " index " + index + ", count " + count);
    }
  }

  private NewOrderResponseDecoder() {}
}
