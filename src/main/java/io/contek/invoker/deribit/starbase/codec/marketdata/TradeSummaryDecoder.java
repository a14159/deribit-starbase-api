package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Decoder for aggressor trade-summary market-data template 30. */
public final class TradeSummaryDecoder {

  public static final int TEMPLATE_ID = 30;
  public static final int BLOCK_LENGTH = 56;
  public static final long FLAG_SELL = 1;
  public static final long FLAG_LIQUIDATION = 2;
  public static final long KNOWN_FLAGS = FLAG_SELL | FLAG_LIQUIDATION;

  public static final int INSTRUMENT_ID_OFFSET = 0;
  public static final int TAKER_ORDER_ID_OFFSET = 8;
  public static final int TOTAL_FILLED_MANTISSA_OFFSET = 16;
  public static final int DEEPEST_PRICE_OFFSET = 24;
  public static final int MARK_PRICE_OFFSET = 32;
  public static final int INDEX_PRICE_OFFSET = 40;
  public static final int TRADE_COUNT_OFFSET = 48;
  public static final int TAKER_FLAGS_OFFSET = 52;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.validateFixed(buffer, messageOffset, TEMPLATE_ID, BLOCK_LENGTH);
    requireNonNull(instrumentId(buffer, messageOffset), "instrumentId");
    requireNonNull(takerOrderId(buffer, messageOffset), "takerOrderId");
    requireNonNull(totalFilledMantissa(buffer, messageOffset), "totalFilledMantissa");
    requireNonNull(deepestPriceMantissa(buffer, messageOffset), "deepestPrice");
    requireNonNull(markPriceMantissa(buffer, messageOffset), "markPrice");
    requireNonNull(indexPriceMantissa(buffer, messageOffset), "indexPrice");
    if (tradeCount(buffer, messageOffset) < 0) {
      throw new StarbaseProtocolException("negative TradeSummary tradeCount");
    }
    validateFlags(takerFlags(buffer, messageOffset), "takerFlags");
  }

  public static long instrumentId(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, INSTRUMENT_ID_OFFSET);
  }

  public static long takerOrderId(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, TAKER_ORDER_ID_OFFSET);
  }

  public static long totalFilledMantissa(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, TOTAL_FILLED_MANTISSA_OFFSET);
  }

  public static long deepestPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, DEEPEST_PRICE_OFFSET);
  }

  public static long markPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, MARK_PRICE_OFFSET);
  }

  public static long indexPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return longValue(buffer, messageOffset, INDEX_PRICE_OFFSET);
  }

  public static int tradeCount(ByteBuffer buffer, int messageOffset) {
    require(buffer, messageOffset);
    return buffer.getInt(body(messageOffset) + TRADE_COUNT_OFFSET);
  }

  public static long takerFlags(ByteBuffer buffer, int messageOffset) {
    require(buffer, messageOffset);
    return Integer.toUnsignedLong(buffer.getInt(body(messageOffset) + TAKER_FLAGS_OFFSET));
  }

  public static boolean isSell(ByteBuffer buffer, int messageOffset) {
    return (takerFlags(buffer, messageOffset) & FLAG_SELL) != 0;
  }

  public static boolean isLiquidation(ByteBuffer buffer, int messageOffset) {
    return (takerFlags(buffer, messageOffset) & FLAG_LIQUIDATION) != 0;
  }

  static void validateFlags(long flags, String field) {
    if ((flags & ~KNOWN_FLAGS) != 0) {
      throw new StarbaseProtocolException("unknown TradeFlags in " + field + ": " + flags);
    }
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
      throw new StarbaseProtocolException("null required TradeSummary " + field);
    }
  }

  private TradeSummaryDecoder() {}
}
