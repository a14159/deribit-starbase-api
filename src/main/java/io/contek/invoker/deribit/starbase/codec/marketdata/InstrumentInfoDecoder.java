package io.contek.invoker.deribit.starbase.codec.marketdata;

import java.nio.ByteBuffer;

/** Decoder for market-data template 14. */
public final class InstrumentInfoDecoder {

  public static final int TEMPLATE_ID = 14;
  public static final int BLOCK_LENGTH = 32;
  public static final int INSTRUMENT_ID_OFFSET = 0;
  public static final int MIN_SELL_PRICE_OFFSET = 8;
  public static final int MAX_BUY_PRICE_OFFSET = 16;
  public static final int MARK_PRICE_OFFSET = 24;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.validateFixed(buffer, messageOffset, TEMPLATE_ID, BLOCK_LENGTH);
  }

  public static long instrumentId(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, INSTRUMENT_ID_OFFSET);
  }

  public static long minSellPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, MIN_SELL_PRICE_OFFSET);
  }

  public static long maxBuyPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, MAX_BUY_PRICE_OFFSET);
  }

  public static long markPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, MARK_PRICE_OFFSET);
  }

  private static long value(ByteBuffer buffer, int messageOffset, int fieldOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
    return buffer.getLong(messageOffset + MarketDataDecoderSupport.BODY_OFFSET + fieldOffset);
  }

  private InstrumentInfoDecoder() {}
}
