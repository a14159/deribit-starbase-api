package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/**
 * Decoder for off-book block-trade fill market-data template 33. Fills of one per-instrument
 * block-trade event are delimited by the message-header transaction flags.
 */
public final class BlockTradeDecoder {

  public static final int TEMPLATE_ID = 33;
  public static final int BLOCK_LENGTH = 78;
  public static final int MATCH_ID_OFFSET = 0;
  public static final int INSTRUMENT_ID_OFFSET = 8;
  public static final int BLOCK_TRADE_ID_OFFSET = 16;
  public static final int BLOCK_RFQ_ID_OFFSET = 24;
  public static final int FILL_QUANTITY_MANTISSA_OFFSET = 32;
  public static final int FILL_PRICE_OFFSET = 40;
  public static final int MARK_PRICE_OFFSET = 48;
  public static final int INDEX_PRICE_OFFSET = 56;
  public static final int IMPLIED_VOLATILITY_OFFSET = 64;
  public static final int TAKER_FLAGS_OFFSET = 72;
  public static final int NUMBER_OF_LEGS_OFFSET = 76;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.validateFixed(buffer, messageOffset, TEMPLATE_ID, BLOCK_LENGTH);
    requireNonNull(matchId(buffer, messageOffset), "matchId");
    requireNonNull(instrumentId(buffer, messageOffset), "instrumentId");
    requireNonNull(blockTradeId(buffer, messageOffset), "blockTradeId");
    requireNonNull(fillQuantityMantissa(buffer, messageOffset), "fillQtyMantissa");
    requireNonNull(fillPriceMantissa(buffer, messageOffset), "fillPrice");
    requireNonNull(markPriceMantissa(buffer, messageOffset), "markPrice");
    requireNonNull(indexPriceMantissa(buffer, messageOffset), "indexPrice");
    if (Double.isInfinite(impliedVolatility(buffer, messageOffset))) {
      throw new StarbaseProtocolException("infinite BlockTrade impliedVolatility");
    }
    TradeSummaryDecoder.validateFlags(takerFlags(buffer, messageOffset), "takerFlags");
  }

  public static long matchId(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, MATCH_ID_OFFSET);
  }

  public static long instrumentId(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, INSTRUMENT_ID_OFFSET);
  }

  public static long blockTradeId(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, BLOCK_TRADE_ID_OFFSET);
  }

  /** Returns the Block RFQ ID, or the SBE null {@code Long.MIN_VALUE} for standard block trades. */
  public static long blockRfqId(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, BLOCK_RFQ_ID_OFFSET);
  }

  public static boolean isBlockRfqIdNull(ByteBuffer buffer, int messageOffset) {
    return blockRfqId(buffer, messageOffset) == Long.MIN_VALUE;
  }

  public static long fillQuantityMantissa(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, FILL_QUANTITY_MANTISSA_OFFSET);
  }

  public static long fillPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, FILL_PRICE_OFFSET);
  }

  public static long markPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, MARK_PRICE_OFFSET);
  }

  public static long indexPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, INDEX_PRICE_OFFSET);
  }

  /** Returns the implied volatility, or the SBE null NaN for non-option instruments. */
  public static double impliedVolatility(ByteBuffer buffer, int messageOffset) {
    require(buffer, messageOffset);
    return buffer.getDouble(body(messageOffset) + IMPLIED_VOLATILITY_OFFSET);
  }

  public static boolean isImpliedVolatilityNull(ByteBuffer buffer, int messageOffset) {
    return Double.isNaN(impliedVolatility(buffer, messageOffset));
  }

  public static long takerFlags(ByteBuffer buffer, int messageOffset) {
    require(buffer, messageOffset);
    return Integer.toUnsignedLong(buffer.getInt(body(messageOffset) + TAKER_FLAGS_OFFSET));
  }

  public static boolean isSell(ByteBuffer buffer, int messageOffset) {
    return (takerFlags(buffer, messageOffset) & TradeSummaryDecoder.FLAG_SELL) != 0;
  }

  public static boolean isLiquidation(ByteBuffer buffer, int messageOffset) {
    return (takerFlags(buffer, messageOffset) & TradeSummaryDecoder.FLAG_LIQUIDATION) != 0;
  }

  public static int numberOfLegs(ByteBuffer buffer, int messageOffset) {
    require(buffer, messageOffset);
    return Short.toUnsignedInt(buffer.getShort(body(messageOffset) + NUMBER_OF_LEGS_OFFSET));
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
      throw new StarbaseProtocolException("null required BlockTrade " + field);
    }
  }

  private BlockTradeDecoder() {}
}
