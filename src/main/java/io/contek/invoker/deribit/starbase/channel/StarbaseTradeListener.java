package io.contek.invoker.deribit.starbase.channel;

/** Allocation-free callback containing one trade and its complete summary context. */
@FunctionalInterface
public interface StarbaseTradeListener {

  void onTrade(
      long matchId,
      long instrumentId,
      long makerOrderId,
      long fillQuantityMantissa,
      long fillPriceMantissa,
      long makerFlags,
      long takerOrderId,
      long totalFilledMantissa,
      long deepestPriceMantissa,
      long markPriceMantissa,
      long indexPriceMantissa,
      long takerFlags,
      int tradeIndex,
      int tradeCount,
      long sequenceNumber,
      long timestampNanos);
}
