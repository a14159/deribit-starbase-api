package io.contek.invoker.deribit.starbase.rest;

public enum StarbaseOrderSide {
  BUY,
  SELL;

  static StarbaseOrderSide parse(String value) {
    return switch (value) {
      case "buy" -> BUY;
      case "sell" -> SELL;
      default -> throw new IllegalArgumentException("unknown order side: " + value);
    };
  }
}
