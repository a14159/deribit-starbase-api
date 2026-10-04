package io.contek.invoker.deribit.starbase.rest;

import io.contek.invoker.deribit.starbase.common.ProductGroup;

public record StarbaseInstrumentFilter(
    String currency,
    StarbaseInstrumentKind kind,
    Boolean expired,
    Long instrumentId,
    ProductGroup productGroup,
    StarbaseInstrumentState state) {

  public static final StarbaseInstrumentFilter ALL = new StarbaseInstrumentFilter(null, null, null);

  public StarbaseInstrumentFilter(String currency, StarbaseInstrumentKind kind, Boolean expired) {
    this(currency, kind, expired, null, null, null);
  }

  public StarbaseInstrumentFilter {
    if (currency != null && currency.isBlank()) {
      throw new IllegalArgumentException("currency must not be blank");
    }
    if (instrumentId != null && instrumentId == Long.MIN_VALUE) {
      throw new IllegalArgumentException("instrumentId is the SBE null sentinel");
    }
  }
}
