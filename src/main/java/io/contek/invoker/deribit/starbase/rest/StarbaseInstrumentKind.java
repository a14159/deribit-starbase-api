package io.contek.invoker.deribit.starbase.rest;

public enum StarbaseInstrumentKind {
  PERP_FUTURE("perp_future"),
  OPTION("option"),
  SPOT("spot"),
  FUTURE_COMBO("future_combo"),
  OPTION_COMBO("option_combo"),
  DATED_FUTURE("dated_future");

  private final String wireValue;

  StarbaseInstrumentKind(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  static StarbaseInstrumentKind parse(String value) {
    for (StarbaseInstrumentKind kind : values()) {
      if (kind.wireValue.equals(value)) return kind;
    }
    throw new IllegalArgumentException("unknown instrument kind: " + value);
  }
}
