package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Decoder for individual-trade market-data template 31. */
public final class TradeDecoder {

  public static final int TEMPLATE_ID = 31;
  public static final int BLOCK_LENGTH = 44;
  public static final int MATCH_ID_OFFSET = 0;
  public static final int INSTRUMENT_ID_OFFSET = 8;
  public static final int MAKER_ORDER_ID_OFFSET = 16;
  public static final int FILL_QUANTITY_MANTISSA_OFFSET = 24;
  public static final int FILL_PRICE_OFFSET = 32;
  public static final int MAKER_FLAGS_OFFSET = 40;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.validateFixed(buffer, messageOffset, TEMPLATE_ID, BLOCK_LENGTH);
    requireNonNull(matchId(buffer, messageOffset), "matchId");
    requireNonNull(instrumentId(buffer, messageOffset), "instrumentId");
    requireNonNull(fillQuantityMantissa(buffer, messageOffset), "fillQtyMantissa");
    requireNonNull(fillPriceMantissa(buffer, messageOffset), "fillPrice");
    TradeSummaryDecoder.validateFlags(makerFlags(buffer, messageOffset), "makerFlags");
  }

  public static long matchId(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, MATCH_ID_OFFSET);
  }

  public static long instrumentId(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, INSTRUMENT_ID_OFFSET);
  }

  public static long makerOrderId(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, MAKER_ORDER_ID_OFFSET);
  }

  public static boolean isMakerOrderIdNull(ByteBuffer buffer, int messageOffset) {
    return makerOrderId(buffer, messageOffset) == Long.MIN_VALUE;
  }

  public static long fillQuantityMantissa(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, FILL_QUANTITY_MANTISSA_OFFSET);
  }

  public static long fillPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, FILL_PRICE_OFFSET);
  }

  public static long makerFlags(ByteBuffer buffer, int messageOffset) {
    require(buffer, messageOffset);
    return Integer.toUnsignedLong(buffer.getInt(body(messageOffset) + MAKER_FLAGS_OFFSET));
  }

  public static boolean isSell(ByteBuffer buffer, int messageOffset) {
    return (makerFlags(buffer, messageOffset) & TradeSummaryDecoder.FLAG_SELL) != 0;
  }

  public static boolean isLiquidation(ByteBuffer buffer, int messageOffset) {
    return (makerFlags(buffer, messageOffset) & TradeSummaryDecoder.FLAG_LIQUIDATION) != 0;
  }

  private static long longValue(ByteBuffer buffer, int messageOffset, int fieldOffset) {
    require(buffer, messageOffset);
    return buffer.getLong(body(messageOffset) + fieldOffset);
  }

  private static void require(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
  }

  private static int body(int messageOffset) {
    return messageOffset + MarketDataDecoderSupport.BODY_OFFSET;
  }

  private static void requireNonNull(long value, String field) {
    if (value == Long.MIN_VALUE) {
      throw new StarbaseProtocolException("null required Trade " + field);
    }
  }

  private TradeDecoder() {}
}
