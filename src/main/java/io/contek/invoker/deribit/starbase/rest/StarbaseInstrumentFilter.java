package io.contek.invoker.deribit.starbase.rest;

public record StarbaseInstrumentFilter(
    String currency, StarbaseInstrumentKind kind, Boolean expired) {

  public static final StarbaseInstrumentFilter ALL = new StarbaseInstrumentFilter(null, null, null);

  public StarbaseInstrumentFilter {
    if (currency != null && currency.isBlank()) {
      throw new IllegalArgumentException("currency must not be blank");
    }
  }
}
