package io.contek.invoker.deribit.starbase.rest;

/** Documented lifecycle-state values for the Starbase REST instrument filter. */
public enum StarbaseInstrumentState {
  OPEN("open"),
  INACTIVE("inactive"),
  SETTLEMENT("settlement"),
  DELIVERED("delivered"),
  LOCKED("locked"),
  HALTED("halted");

  private final String wireValue;

  StarbaseInstrumentState(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }
}
