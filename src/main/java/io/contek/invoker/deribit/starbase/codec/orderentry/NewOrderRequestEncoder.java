package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.Price9Codec;
import java.nio.ByteBuffer;

/** Hardcoded encoder for order-entry schema-v11 NewOrderRequest (template 100). */
public final class NewOrderRequestEncoder {

  public static final int TEMPLATE_ID = 100;
  public static final int BODY_LENGTH = 63;
  public static final int MESSAGE_LENGTH = SessionCodecSupport.BODY_OFFSET + BODY_LENGTH;
  public static final int ENCODED_LENGTH = 96;

  public static final int CANCEL_ON_DISCONNECT = 1;
  public static final int POST_ONLY = 1 << 1;
  public static final int POST_ONLY_REJECT = 1 << 2;
  public static final int MARKET_LIMIT = 1 << 3;
  public static final int MMP = 1 << 4;
  public static final int RESET_MMP = 1 << 5;
  public static final int KNOWN_FLAGS = 0x3f;

  public static int encodeLimit(
      ByteBuffer buffer,
      int offset,
      long clientOrderId,
      long correlationId,
      long instrumentId,
      long priceMantissa,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      long selfMatchPreventionId,
      int side,
      int timeInForce,
      int flags,
      int selfTradingMode,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    validateLimit(
        clientOrderId,
        instrumentId,
        priceMantissa,
        quantityMantissa,
        quantityExponent,
        showQuantityNull,
        showQuantityMantissa,
        side,
        timeInForce,
        flags,
        selfTradingMode);
    return encode(
        buffer,
        offset,
        clientOrderId,
        correlationId,
        instrumentId,
        priceMantissa,
        quantityMantissa,
        quantityExponent,
        showQuantityNull,
        showQuantityMantissa,
        selfMatchPreventionId,
        side,
        timeInForce,
        flags,
        selfTradingMode,
        sequence,
        lastProcessedSequence,
        sendTimeNanos);
  }

  public static int encodeMarket(
      ByteBuffer buffer,
      int offset,
      long clientOrderId,
      long correlationId,
      long instrumentId,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      long selfMatchPreventionId,
      int side,
      int timeInForce,
      int flags,
      int selfTradingMode,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    int marketFlags = flags | MARKET_LIMIT;
    validateMarket(
        clientOrderId,
        instrumentId,
        quantityMantissa,
        quantityExponent,
        showQuantityNull,
        showQuantityMantissa,
        side,
        timeInForce,
        marketFlags,
        selfTradingMode);
    return encode(
        buffer,
        offset,
        clientOrderId,
        correlationId,
        instrumentId,
        Price9Codec.NULL_MANTISSA,
        quantityMantissa,
        quantityExponent,
        showQuantityNull,
        showQuantityMantissa,
        selfMatchPreventionId,
        side,
        timeInForce,
        marketFlags,
        selfTradingMode,
        sequence,
        lastProcessedSequence,
        sendTimeNanos);
  }

  private static int encode(
      ByteBuffer buffer,
      int offset,
      long clientOrderId,
      long correlationId,
      long instrumentId,
      long priceMantissa,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      long selfMatchPreventionId,
      int side,
      int timeInForce,
      int flags,
      int selfTradingMode,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    int encoded =
        SessionCodecSupport.encodeHeader(
            buffer,
            offset,
            TEMPLATE_ID,
            BODY_LENGTH,
            sequence,
            lastProcessedSequence,
            sendTimeNanos);
    int body = offset + SessionCodecSupport.BODY_OFFSET;
    buffer.putLong(body, clientOrderId);
    buffer.putLong(body + 8, correlationId);
    buffer.putLong(body + 16, instrumentId);
    if (priceMantissa == Price9Codec.NULL_MANTISSA) {
      Price9Codec.putNull(buffer, body + 24);
    } else {
      Price9Codec.put(buffer, body + 24, priceMantissa);
    }
    Decimal72Codec.put(buffer, body + 32, quantityMantissa, quantityExponent);
    if (showQuantityNull) {
      Decimal72Codec.putNull(buffer, body + 41);
    } else {
      Decimal72Codec.put(buffer, body + 41, showQuantityMantissa, quantityExponent);
    }
    buffer.putLong(body + 50, selfMatchPreventionId);
    buffer.put(body + 58, (byte) side);
    buffer.put(body + 59, (byte) timeInForce);
    buffer.putShort(body + 60, (short) flags);
    buffer.put(body + 62, (byte) selfTradingMode);
    SessionCodecSupport.finishEncode(buffer, offset, MESSAGE_LENGTH);
    return encoded;
  }

  public static void validateLimit(
      long clientOrderId,
      long instrumentId,
      long priceMantissa,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      int side,
      int timeInForce,
      int flags,
      int selfTradingMode) {
    if (priceMantissa == Price9Codec.NULL_MANTISSA || (flags & MARKET_LIMIT) != 0) {
      throw new IllegalArgumentException("limit order requires a price and no marketLimit flag");
    }
    validateArguments(
        clientOrderId, instrumentId, quantityMantissa, quantityExponent, showQuantityNull,
        showQuantityMantissa, side, timeInForce, flags, selfTradingMode);
  }

  public static void validateMarket(
      long clientOrderId,
      long instrumentId,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      int side,
      int timeInForce,
      int flags,
      int selfTradingMode) {
    validateArguments(
        clientOrderId, instrumentId, quantityMantissa, quantityExponent, showQuantityNull,
        showQuantityMantissa, side, timeInForce, flags | MARKET_LIMIT, selfTradingMode);
  }

  private static void validateArguments(
      long clientOrderId,
      long instrumentId,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      int side,
      int timeInForce,
      int flags,
      int selfTradingMode) {
    if (clientOrderId < 0 || instrumentId < 0) {
      throw new IllegalArgumentException("order identifiers must be non-negative");
    }
    if (quantityMantissa <= 0 || quantityExponent <= Byte.MIN_VALUE
        || quantityExponent > Byte.MAX_VALUE) {
      throw new IllegalArgumentException("order quantity must be a positive Decimal72");
    }
    if (!showQuantityNull
        && (showQuantityMantissa <= 0 || showQuantityMantissa > quantityMantissa)) {
      throw new IllegalArgumentException("show quantity must be positive and no greater than quantity");
    }
    if (side != 1 && side != -1) {
      throw new IllegalArgumentException("new-order side must be BUY or SELL");
    }
    if (timeInForce < -2 || timeInForce > Byte.MAX_VALUE) {
      throw new IllegalArgumentException("invalid time in force");
    }
    if ((flags & ~KNOWN_FLAGS) != 0 || (flags & POST_ONLY) != 0 && (flags & POST_ONLY_REJECT) != 0) {
      throw new IllegalArgumentException("invalid order flags");
    }
    if (selfTradingMode != 0 && selfTradingMode != 1) {
      throw new IllegalArgumentException("invalid self-trading mode");
    }
  }

  private NewOrderRequestEncoder() {}
}
