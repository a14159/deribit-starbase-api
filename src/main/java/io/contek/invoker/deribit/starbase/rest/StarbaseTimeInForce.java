package io.contek.invoker.deribit.starbase.rest;

public enum StarbaseTimeInForce {
  GTC,
  IOC,
  FOK,
  GTD;

  static StarbaseTimeInForce parse(String value) {
    try {
      return valueOf(value);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("unknown time_in_force: " + value, invalid);
    }
  }
}
