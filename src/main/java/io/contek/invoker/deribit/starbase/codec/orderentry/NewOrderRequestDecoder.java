package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.Price9Codec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry schema-v11 NewOrderRequest (template 100). */
public final class NewOrderRequestDecoder {

  public static final int TEMPLATE_ID = NewOrderRequestEncoder.TEMPLATE_ID;
  public static final int BODY_LENGTH = NewOrderRequestEncoder.BODY_LENGTH;

  private static final int CLIENT_ORDER_ID = 0;
  private static final int CORRELATION_ID = 8;
  private static final int INSTRUMENT_ID = 16;
  private static final int PRICE = 24;
  private static final int QUANTITY = 32;
  private static final int SHOW_QUANTITY = 41;
  private static final int SELF_MATCH_PREVENTION_ID = 50;
  private static final int SIDE = 58;
  private static final int TIME_IN_FORCE = 59;
  private static final int FLAGS = 60;
  private static final int SELF_TRADING_MODE = 62;

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(buffer, offset, TEMPLATE_ID, BODY_LENGTH);
    long price = priceMantissa(buffer, offset);
    long quantity = quantityMantissa(buffer, offset);
    int quantityExponent = quantityExponent(buffer, offset);
    int flags = flags(buffer, offset);
    if (clientOrderId(buffer, offset) == Long.MIN_VALUE || instrumentId(buffer, offset) < 0) {
      throw new StarbaseProtocolException("invalid new-order identifier");
    }
    if (quantity <= 0 || quantityExponent == Decimal72Codec.NULL_EXPONENT) {
      throw new StarbaseProtocolException("invalid new-order quantity");
    }
    boolean showNull = isShowQuantityNull(buffer, offset);
    long showMantissa = showQuantityMantissa(buffer, offset);
    int showExponent = showQuantityExponent(buffer, offset);
    if (!showNull
        && (showMantissa == Decimal72Codec.NULL_MANTISSA
            || showExponent == Decimal72Codec.NULL_EXPONENT
            || showMantissa <= 0)) {
      throw new StarbaseProtocolException("invalid new-order show quantity");
    }
    if (side(buffer, offset) != 1 && side(buffer, offset) != -1) {
      throw new StarbaseProtocolException("invalid new-order side");
    }
    if (timeInForce(buffer, offset) < -2) {
      throw new StarbaseProtocolException("invalid new-order time in force");
    }
    if ((flags & ~NewOrderRequestEncoder.KNOWN_FLAGS) != 0
        || (flags & NewOrderRequestEncoder.POST_ONLY) != 0
            && (flags & NewOrderRequestEncoder.POST_ONLY_REJECT) != 0) {
      throw new StarbaseProtocolException("invalid new-order flags");
    }
    boolean market = (flags & NewOrderRequestEncoder.MARKET_LIMIT) != 0;
    if (market != (price == Price9Codec.NULL_MANTISSA)) {
      throw new StarbaseProtocolException("market flag and price nullity disagree");
    }
    int mode = selfTradingMode(buffer, offset);
    if (mode != 0 && mode != 1) {
      throw new StarbaseProtocolException("invalid self-trading mode");
    }
  }

  public static long clientOrderId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + CLIENT_ORDER_ID);
  }

  public static long correlationId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + CORRELATION_ID);
  }

  public static long instrumentId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + INSTRUMENT_ID);
  }

  public static long priceMantissa(ByteBuffer buffer, int offset) {
    return Price9Codec.mantissa(buffer, body(offset) + PRICE);
  }

  public static boolean isMarket(ByteBuffer buffer, int offset) {
    return priceMantissa(buffer, offset) == Price9Codec.NULL_MANTISSA
        && (flags(buffer, offset) & NewOrderRequestEncoder.MARKET_LIMIT) != 0;
  }

  public static long quantityMantissa(ByteBuffer buffer, int offset) {
    return Decimal72Codec.mantissa(buffer, body(offset) + QUANTITY);
  }

  public static int quantityExponent(ByteBuffer buffer, int offset) {
    return Decimal72Codec.exponent(buffer, body(offset) + QUANTITY);
  }

  public static boolean isShowQuantityNull(ByteBuffer buffer, int offset) {
    return Decimal72Codec.isNull(buffer, body(offset) + SHOW_QUANTITY);
  }

  public static long showQuantityMantissa(ByteBuffer buffer, int offset) {
    return Decimal72Codec.mantissa(buffer, body(offset) + SHOW_QUANTITY);
  }

  public static int showQuantityExponent(ByteBuffer buffer, int offset) {
    return Decimal72Codec.exponent(buffer, body(offset) + SHOW_QUANTITY);
  }

  public static long selfMatchPreventionId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + SELF_MATCH_PREVENTION_ID);
  }

  public static int side(ByteBuffer buffer, int offset) {
    return buffer.get(body(offset) + SIDE);
  }

  public static int timeInForce(ByteBuffer buffer, int offset) {
    return buffer.get(body(offset) + TIME_IN_FORCE);
  }

  public static int flags(ByteBuffer buffer, int offset) {
    return Short.toUnsignedInt(buffer.getShort(body(offset) + FLAGS));
  }

  public static int selfTradingMode(ByteBuffer buffer, int offset) {
    return buffer.get(body(offset) + SELF_TRADING_MODE);
  }

  private static int body(int offset) {
    return offset + SessionCodecSupport.BODY_OFFSET;
  }

  private NewOrderRequestDecoder() {}
}
