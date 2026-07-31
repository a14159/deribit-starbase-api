package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

final class BookPutDecoderSupport {

  static final int BLOCK_LENGTH = 40;
  static final int ORDER_ID_OFFSET = 0;
  static final int INSTRUMENT_ID_OFFSET = 8;
  static final int QUANTITY_MANTISSA_OFFSET = 16;
  static final int PRICE_OFFSET = 24;
  static final int SORT_ORDER_ID_OFFSET = 32;

  static void validate(ByteBuffer buffer, int messageOffset, int templateId) {
    MarketDataDecoderSupport.validateFixed(buffer, messageOffset, templateId, BLOCK_LENGTH);
    requireNonNull(orderId(buffer, messageOffset), "orderId");
    requireNonNull(instrumentId(buffer, messageOffset), "instrumentId");
    requireNonNull(quantityMantissa(buffer, messageOffset), "quantityMantissa");
    requireNonNull(priceMantissa(buffer, messageOffset), "price");
    requireNonNull(sortOrderId(buffer, messageOffset), "sortOrderId");
  }

  static long orderId(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, ORDER_ID_OFFSET);
  }

  static long instrumentId(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, INSTRUMENT_ID_OFFSET);
  }

  static long quantityMantissa(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, QUANTITY_MANTISSA_OFFSET);
  }

  static long priceMantissa(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, PRICE_OFFSET);
  }

  static long sortOrderId(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, SORT_ORDER_ID_OFFSET);
  }

  private static long value(ByteBuffer buffer, int messageOffset, int fieldOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
    return buffer.getLong(messageOffset + MarketDataDecoderSupport.BODY_OFFSET + fieldOffset);
  }

  private static void requireNonNull(long value, String field) {
    if (value == Long.MIN_VALUE) {
      throw new StarbaseProtocolException("null required book put " + field);
    }
  }

  private BookPutDecoderSupport() {}
}
