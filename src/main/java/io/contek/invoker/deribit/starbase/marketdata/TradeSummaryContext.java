package io.contek.invoker.deribit.starbase.marketdata;

import io.contek.invoker.deribit.starbase.channel.StarbaseTradeChannel;
import io.contek.invoker.deribit.starbase.codec.marketdata.TradeDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.TradeSummaryDecoder;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/**
 * Fail-closed allocation-free scope for TradeSummary followed by exactly N Trade messages.
 *
 * <p>Live publication can resume mid-summary after startup or a fresh-snapshot recovery, so a reset
 * context first joins: Trades whose TradeSummary was not observed are skipped until the next
 * TradeSummary. Once aligned, framing failures fail closed until the next reset.
 */
final class TradeSummaryContext {

  private long instrumentId;
  private long takerOrderId;
  private long totalFilled;
  private long deepestPrice;
  private long markPrice;
  private long indexPrice;
  private long takerFlags;
  private int tradeCount;
  private int tradeIndex;
  private boolean open;
  private boolean failed;
  private boolean joining = true;

  void onSummary(ByteBuffer buffer, int offset) {
    requireHealthy();
    if (open) {
      fail("nested TradeSummary");
    }
    joining = false;
    instrumentId = TradeSummaryDecoder.instrumentId(buffer, offset);
    takerOrderId = TradeSummaryDecoder.takerOrderId(buffer, offset);
    totalFilled = TradeSummaryDecoder.totalFilledMantissa(buffer, offset);
    deepestPrice = TradeSummaryDecoder.deepestPriceMantissa(buffer, offset);
    markPrice = TradeSummaryDecoder.markPriceMantissa(buffer, offset);
    indexPrice = TradeSummaryDecoder.indexPriceMantissa(buffer, offset);
    takerFlags = TradeSummaryDecoder.takerFlags(buffer, offset);
    tradeCount = TradeSummaryDecoder.tradeCount(buffer, offset);
    tradeIndex = 0;
    open = tradeCount > 0;
  }

  void onTrade(
      ByteBuffer buffer,
      int offset,
      long sequenceNumber,
      long timestampNanos,
      TradeChannelRouter channels) {
    requireHealthy();
    if (!open) {
      if (joining) {
        return;
      }
      fail("Trade without open TradeSummary");
    }
    long tradeInstrumentId = TradeDecoder.instrumentId(buffer, offset);
    if (tradeInstrumentId != instrumentId) {
      fail("Trade instrument does not match TradeSummary");
    }
    StarbaseTradeChannel channel = channels.existing(instrumentId);
    if (channel != null) {
      channel.publish(
          TradeDecoder.matchId(buffer, offset),
          instrumentId,
          TradeDecoder.makerOrderId(buffer, offset),
          TradeDecoder.fillQuantityMantissa(buffer, offset),
          TradeDecoder.fillPriceMantissa(buffer, offset),
          TradeDecoder.makerFlags(buffer, offset),
          takerOrderId,
          totalFilled,
          deepestPrice,
          markPrice,
          indexPrice,
          takerFlags,
          tradeIndex,
          tradeCount,
          sequenceNumber,
          timestampNanos);
    }
    tradeIndex++;
    if (tradeIndex == tradeCount) {
      open = false;
    }
  }

  void onEndOfCycle() {
    requireHealthy();
    if (open) {
      fail("EndOfCycle before all TradeSummary trades");
    }
  }

  /** Forgets any open summary or failure and rejoins at the next TradeSummary. */
  void reset() {
    open = false;
    failed = false;
    joining = true;
    tradeCount = 0;
    tradeIndex = 0;
  }

  private void requireHealthy() {
    if (failed) {
      throw new StarbaseProtocolException("trade summary context is failed");
    }
  }

  private void fail(String message) {
    failed = true;
    open = false;
    throw new StarbaseProtocolException(message);
  }
}
