package io.contek.invoker.deribit.starbase.rest;

import io.contek.invoker.deribit.starbase.common.ProductGroup;
import java.math.BigDecimal;

/** Immutable control-plane view of one Starbase REST instrument. */
public record StarbaseInstrument(
    long instrumentId,
    String instrumentName,
    StarbaseInstrumentKind kind,
    Long indexId,
    ProductGroup productGroup,
    String baseCurrency,
    String quoteCurrency,
    String settlementCurrency,
    BigDecimal tickSize,
    BigDecimal strike,
    String optionType,
    boolean active,
    Long expirationTimestamp,
    Long creationTimestamp,
    BigDecimal minTradeAmount,
    BigDecimal contractSize,
    String settlementPeriod,
    BigDecimal makerCommission,
    BigDecimal takerCommission,
    BigDecimal blockTradeCommission) {}
