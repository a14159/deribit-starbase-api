package io.contek.invoker.deribit.starbase.rest;

import io.contek.invoker.deribit.starbase.common.ProductGroup;
import java.math.BigDecimal;

/** Immutable portfolio-snapshot order retaining the exact SBE identity and REST decimals. */
public record StarbaseOpenOrder(
    long orderId,
    String instrumentName,
    StarbaseOrderSide side,
    BigDecimal price,
    BigDecimal amount,
    BigDecimal filledAmount,
    BigDecimal averagePrice,
    StarbaseRestOrderState state,
    StarbaseRestOrderType type,
    StarbaseTimeInForce timeInForce,
    Boolean postOnly,
    Boolean rejectPostOnly,
    Boolean reduceOnly,
    Long creationTimestamp,
    Long lastUpdateTimestamp,
    String label,
    Boolean api,
    BigDecimal maxShow,
    BigDecimal profitLoss,
    BigDecimal commission,
    long instrumentId,
    ProductGroup productGroup) {

  /**
   * Source-compatible constructor for observations without authoritative instrument metadata.
   * The unknown identity cannot satisfy order-state reconciliation; recovery snapshots must use
   * the canonical constructor with an exact non-null-sentinel instrument ID.
   */
  public StarbaseOpenOrder(
      long orderId,
      String instrumentName,
      StarbaseOrderSide side,
      BigDecimal price,
      BigDecimal amount,
      BigDecimal filledAmount,
      BigDecimal averagePrice,
      StarbaseRestOrderState state,
      StarbaseRestOrderType type,
      StarbaseTimeInForce timeInForce,
      Boolean postOnly,
      Boolean rejectPostOnly,
      Boolean reduceOnly,
      Long creationTimestamp,
      Long lastUpdateTimestamp,
      String label,
      Boolean api,
      BigDecimal maxShow,
      BigDecimal profitLoss,
      BigDecimal commission) {
    this(orderId, instrumentName, side, price, amount, filledAmount, averagePrice, state, type,
        timeInForce, postOnly, rejectPostOnly, reduceOnly, creationTimestamp, lastUpdateTimestamp,
        label, api, maxShow, profitLoss, commission, Long.MIN_VALUE, null);
  }
}
