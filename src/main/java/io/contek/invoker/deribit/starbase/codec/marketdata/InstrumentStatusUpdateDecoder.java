package io.contek.invoker.deribit.starbase.codec.marketdata;

import java.nio.ByteBuffer;

/** Decoder for market-data template 16. */
public final class InstrumentStatusUpdateDecoder {

  public static final int TEMPLATE_ID = 16;
  public static final int BLOCK_LENGTH = 9;
  public static final int INSTRUMENT_ID_OFFSET = 0;
  public static final int TRADING_STATUS_OFFSET = 8;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.validateFixed(buffer, messageOffset, TEMPLATE_ID, BLOCK_LENGTH);
    if (instrumentId(buffer, messageOffset) == Long.MIN_VALUE) {
      throw new io.contek.invoker.deribit.starbase.common.StarbaseProtocolException(
          "null required InstrumentStatusUpdate instrumentId");
    }
    InstrumentDefinitionDecoder.validateStatus(tradingStatus(buffer, messageOffset));
  }

  public static long instrumentId(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
    return buffer.getLong(
        messageOffset + MarketDataDecoderSupport.BODY_OFFSET + INSTRUMENT_ID_OFFSET);
  }

  public static int tradingStatus(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
    return buffer.get(
        messageOffset + MarketDataDecoderSupport.BODY_OFFSET + TRADING_STATUS_OFFSET);
  }

  private InstrumentStatusUpdateDecoder() {}
}
