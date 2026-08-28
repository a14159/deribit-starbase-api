package io.contek.invoker.deribit.starbase.codec.marketdata;

import java.nio.ByteBuffer;

/** Decoder for corrected market-data-v1 IndexInfo (template 12). */
public final class IndexInfoDecoder {

  public static final int TEMPLATE_ID = 12;
  public static final int BLOCK_LENGTH = 16;
  public static final int INDEX_ID_OFFSET = 0;
  public static final int INDEX_PRICE_OFFSET = 8;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.validateFixed(buffer, messageOffset, TEMPLATE_ID, BLOCK_LENGTH);
  }

  public static long indexId(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, INDEX_ID_OFFSET);
  }

  public static long indexPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, INDEX_PRICE_OFFSET);
  }

  private static long value(ByteBuffer buffer, int messageOffset, int fieldOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
    return buffer.getLong(messageOffset + MarketDataDecoderSupport.BODY_OFFSET + fieldOffset);
  }

  private IndexInfoDecoder() {}
}
