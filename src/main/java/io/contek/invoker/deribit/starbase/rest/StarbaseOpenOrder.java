package io.contek.invoker.deribit.starbase.rest;

import java.math.BigDecimal;

/** Immutable portfolio-snapshot order retaining the REST identifier and decimal values exactly. */
public record StarbaseOpenOrder(
    String orderId,
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
    Boolean reduceOnly,
    Long creationTimestamp,
    Long lastUpdateTimestamp,
    String label,
    Boolean api,
    BigDecimal maxShow,
    BigDecimal profitLoss,
    BigDecimal commission) {}
