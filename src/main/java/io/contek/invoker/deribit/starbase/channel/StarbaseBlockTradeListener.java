package io.contek.invoker.deribit.starbase.channel;

/**
 * Allocation-free callback containing one off-book block-trade fill. {@code fillIndex} and
 * {@code lastFill} locate the fill within its per-instrument block-trade transaction.
 */
@FunctionalInterface
public interface StarbaseBlockTradeListener {

  /**
   * @param blockRfqId the Block RFQ ID, or {@code Long.MIN_VALUE} for a standard block trade
   * @param impliedVolatility the implied volatility, or NaN for a non-option instrument
   */
  void onBlockTrade(
      long matchId,
      long instrumentId,
      long blockTradeId,
      long blockRfqId,
      long fillQuantityMantissa,
      long fillPriceMantissa,
      long markPriceMantissa,
      long indexPriceMantissa,
      double impliedVolatility,
      long takerFlags,
      int numberOfLegs,
      int fillIndex,
      boolean lastFill,
      long sequenceNumber,
      long timestampNanos);
}
