package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

final class BookMutationDecoderSupport {

  static final int DELETE_BLOCK_LENGTH = 16;
  static final int REDUCED_BLOCK_LENGTH = 24;
  static final int ORDER_ID_OFFSET = 0;
  static final int INSTRUMENT_ID_OFFSET = 8;
  static final int QUANTITY_MANTISSA_OFFSET = 16;

  static void validateDelete(ByteBuffer buffer, int messageOffset, int templateId) {
    MarketDataDecoderSupport.validateFixed(
        buffer, messageOffset, templateId, DELETE_BLOCK_LENGTH);
    requireNonNull(orderId(buffer, messageOffset, DELETE_BLOCK_LENGTH), "orderId");
    requireNonNull(
        instrumentId(buffer, messageOffset, DELETE_BLOCK_LENGTH), "instrumentId");
  }

  static void validateReduced(ByteBuffer buffer, int messageOffset, int templateId) {
    MarketDataDecoderSupport.validateFixed(
        buffer, messageOffset, templateId, REDUCED_BLOCK_LENGTH);
    requireNonNull(orderId(buffer, messageOffset, REDUCED_BLOCK_LENGTH), "orderId");
    requireNonNull(
        instrumentId(buffer, messageOffset, REDUCED_BLOCK_LENGTH), "instrumentId");
    requireNonNull(quantityMantissa(buffer, messageOffset), "quantityMantissa");
  }

  static long orderId(ByteBuffer buffer, int messageOffset, int blockLength) {
    return value(buffer, messageOffset, blockLength, ORDER_ID_OFFSET);
  }

  static long instrumentId(ByteBuffer buffer, int messageOffset, int blockLength) {
    return value(buffer, messageOffset, blockLength, INSTRUMENT_ID_OFFSET);
  }

  static long quantityMantissa(ByteBuffer buffer, int messageOffset) {
    return value(
        buffer, messageOffset, REDUCED_BLOCK_LENGTH, QUANTITY_MANTISSA_OFFSET);
  }

  private static long value(
      ByteBuffer buffer, int messageOffset, int blockLength, int fieldOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, blockLength);
    return buffer.getLong(
        messageOffset + MarketDataDecoderSupport.BODY_OFFSET + fieldOffset);
  }

  private static void requireNonNull(long value, String field) {
    if (value == Long.MIN_VALUE) {
      throw new StarbaseProtocolException("null required book mutation " + field);
    }
  }

  private BookMutationDecoderSupport() {}
}
