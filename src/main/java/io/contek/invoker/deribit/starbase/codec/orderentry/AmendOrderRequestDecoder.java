package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.Price9Codec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry schema-v11 AmendOrderRequest (template 110). */
public final class AmendOrderRequestDecoder {

  public static final int TEMPLATE_ID = AmendOrderRequestEncoder.TEMPLATE_ID;
  public static final int BODY_LENGTH = AmendOrderRequestEncoder.BODY_LENGTH;

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(buffer, offset, TEMPLATE_ID, BODY_LENGTH);
    if (clientOrderId(buffer, offset) == Long.MIN_VALUE || instrumentId(buffer, offset) < 0) {
      throw new StarbaseProtocolException("invalid amend identifier");
    }
    if (priceMantissa(buffer, offset) == Price9Codec.NULL_MANTISSA) {
      throw new StarbaseProtocolException("null amend price");
    }
    long quantity = quantityMantissa(buffer, offset);
    int quantityExponent = quantityExponent(buffer, offset);
    if (quantity <= 0 || quantityExponent == Decimal72Codec.NULL_EXPONENT) {
      throw new StarbaseProtocolException("invalid amend quantity");
    }
    boolean showNull = isShowQuantityNull(buffer, offset);
    long show = showQuantityMantissa(buffer, offset);
    int showExponent = showQuantityExponent(buffer, offset);
    if (!showNull
        && (show == Decimal72Codec.NULL_MANTISSA
            || showExponent == Decimal72Codec.NULL_EXPONENT
            || show <= 0)) {
      throw new StarbaseProtocolException("invalid amend show quantity");
    }
    int flags = flags(buffer, offset);
    if ((flags & ~AmendOrderRequestEncoder.KNOWN_FLAGS) != 0
        || (flags & AmendOrderRequestEncoder.POST_ONLY) != 0
            && (flags & AmendOrderRequestEncoder.POST_ONLY_REJECT) != 0) {
      throw new StarbaseProtocolException("invalid amend flags");
    }
  }

  public static long clientOrderId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset));
  }

  public static long correlationId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 8);
  }

  public static long instrumentId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 16);
  }

  public static long priceMantissa(ByteBuffer buffer, int offset) {
    return Price9Codec.mantissa(buffer, body(offset) + 24);
  }

  public static long quantityMantissa(ByteBuffer buffer, int offset) {
    return Decimal72Codec.mantissa(buffer, body(offset) + 32);
  }

  public static int quantityExponent(ByteBuffer buffer, int offset) {
    return Decimal72Codec.exponent(buffer, body(offset) + 32);
  }

  public static boolean isShowQuantityNull(ByteBuffer buffer, int offset) {
    return Decimal72Codec.isNull(buffer, body(offset) + 41);
  }

  public static long showQuantityMantissa(ByteBuffer buffer, int offset) {
    return Decimal72Codec.mantissa(buffer, body(offset) + 41);
  }

  public static int showQuantityExponent(ByteBuffer buffer, int offset) {
    return Decimal72Codec.exponent(buffer, body(offset) + 41);
  }

  public static int flags(ByteBuffer buffer, int offset) {
    return Short.toUnsignedInt(buffer.getShort(body(offset) + 50));
  }

  private static int body(int offset) {
    return offset + SessionCodecSupport.BODY_OFFSET;
  }

  private AmendOrderRequestDecoder() {}
}
