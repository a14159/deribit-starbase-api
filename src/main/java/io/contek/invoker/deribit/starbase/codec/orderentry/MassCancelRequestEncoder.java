package io.contek.invoker.deribit.starbase.codec.orderentry;

import java.nio.ByteBuffer;

/** Hardcoded encoder for order-entry schema-v11 MassCancelRequest (template 140). */
public final class MassCancelRequestEncoder {

  public static final int TEMPLATE_ID = 140;
  public static final int BODY_LENGTH = 26;
  public static final long NULL_ID = Long.MIN_VALUE;

  public static int encode(
      ByteBuffer buffer,
      int offset,
      long correlationId,
      long currencyPairId,
      long instrumentId,
      int productType,
      int side,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    validateScope(currencyPairId, instrumentId, productType, side);
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
    buffer.putLong(body, correlationId);
    buffer.putLong(body + 8, currencyPairId);
    buffer.putLong(body + 16, instrumentId);
    buffer.put(body + 24, (byte) productType);
    buffer.put(body + 25, (byte) side);
    SessionCodecSupport.finishEncode(buffer, offset, 58);
    return encoded;
  }

  public static void validateScope(long currencyPairId, long instrumentId, int productType, int side) {
    if (currencyPairId == NULL_ID && instrumentId == NULL_ID) {
      throw new IllegalArgumentException("mass cancel requires a currency-pair or instrument scope");
    }
    if (currencyPairId < 0 && currencyPairId != NULL_ID
        || instrumentId < 0 && instrumentId != NULL_ID) {
      throw new IllegalArgumentException("invalid mass-cancel scope identifier");
    }
    if (productType < 0 || productType > 5) {
      throw new IllegalArgumentException("invalid mass-cancel product type");
    }
    if (side < -1 || side > 1) {
      throw new IllegalArgumentException("invalid mass-cancel side");
    }
  }

  private MassCancelRequestEncoder() {}
}
