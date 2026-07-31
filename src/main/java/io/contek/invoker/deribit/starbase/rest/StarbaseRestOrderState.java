package io.contek.invoker.deribit.starbase.rest;

public enum StarbaseRestOrderState {
  OPEN;

  static StarbaseRestOrderState parse(String value) {
    if (!"open".equals(value)) throw new IllegalArgumentException("unknown order state: " + value);
    return OPEN;
  }
}
