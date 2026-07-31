package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.Price9Codec;
import java.nio.ByteBuffer;

/** Hardcoded encoder for order-entry schema-v11 AmendOrderRequest (template 110). */
public final class AmendOrderRequestEncoder {

  public static final int TEMPLATE_ID = 110;
  public static final int BODY_LENGTH = 52;
  public static final int MESSAGE_LENGTH = SessionCodecSupport.BODY_OFFSET + BODY_LENGTH;
  public static final int ENCODED_LENGTH = 88;

  public static final int POST_ONLY = 1 << 1;
  public static final int POST_ONLY_REJECT = 1 << 2;
  public static final int KNOWN_FLAGS = POST_ONLY | POST_ONLY_REJECT;

  public static int encode(
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
      int flags,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    validateArguments(
        clientOrderId,
        instrumentId,
        priceMantissa,
        quantityMantissa,
        quantityExponent,
        showQuantityNull,
        showQuantityMantissa,
        flags);
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
    Price9Codec.put(buffer, body + 24, priceMantissa);
    Decimal72Codec.put(buffer, body + 32, quantityMantissa, quantityExponent);
    if (showQuantityNull) {
      Decimal72Codec.putNull(buffer, body + 41);
    } else {
      Decimal72Codec.put(buffer, body + 41, showQuantityMantissa, quantityExponent);
    }
    buffer.putShort(body + 50, (short) flags);
    SessionCodecSupport.finishEncode(buffer, offset, MESSAGE_LENGTH);
    return encoded;
  }

  public static void validateArguments(
      long clientOrderId,
      long instrumentId,
      long priceMantissa,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      int flags) {
    if (clientOrderId < 0 || instrumentId < 0) {
      throw new IllegalArgumentException("amend identifiers must be non-negative");
    }
    if (priceMantissa == Price9Codec.NULL_MANTISSA) {
      throw new IllegalArgumentException("amend price is required");
    }
    if (quantityMantissa <= 0
        || quantityExponent <= Byte.MIN_VALUE
        || quantityExponent > Byte.MAX_VALUE) {
      throw new IllegalArgumentException("amend quantity must be a positive Decimal72");
    }
    if (!showQuantityNull
        && (showQuantityMantissa <= 0 || showQuantityMantissa > quantityMantissa)) {
      throw new IllegalArgumentException(
          "amend show quantity must be positive and no greater than quantity");
    }
    if ((flags & ~KNOWN_FLAGS) != 0
        || (flags & POST_ONLY) != 0 && (flags & POST_ONLY_REJECT) != 0) {
      throw new IllegalArgumentException("invalid amend flags");
    }
  }

  private AmendOrderRequestEncoder() {}
}
