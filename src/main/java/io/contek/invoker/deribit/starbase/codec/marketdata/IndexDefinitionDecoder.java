package io.contek.invoker.deribit.starbase.codec.marketdata;

import java.nio.ByteBuffer;

/** Decoder for market-data template 11. */
public final class IndexDefinitionDecoder {

  public static final int TEMPLATE_ID = 11;
  public static final int BLOCK_LENGTH = 136;
  public static final int INDEX_ID_OFFSET = 0;
  public static final int NAME_OFFSET = 8;
  public static final int NAME_LENGTH = 128;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.validateFixed(buffer, messageOffset, TEMPLATE_ID, BLOCK_LENGTH);
  }

  public static long indexId(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
    return buffer.getLong(messageOffset + MarketDataDecoderSupport.BODY_OFFSET + INDEX_ID_OFFSET);
  }

  public static int nameLength(ByteBuffer buffer, int messageOffset) {
    return MarketDataDecoderSupport.fixedAsciiLength(
        buffer, messageOffset, NAME_OFFSET, NAME_LENGTH);
  }

  public static int nameByte(ByteBuffer buffer, int messageOffset, int index) {
    return MarketDataDecoderSupport.fixedAsciiByte(
        buffer, messageOffset, NAME_OFFSET, NAME_LENGTH, index);
  }

  private IndexDefinitionDecoder() {}
}
