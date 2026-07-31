package io.contek.invoker.deribit.starbase.rest;

public enum StarbaseRestOrderType {
  LIMIT,
  MARKET;

  static StarbaseRestOrderType parse(String value) {
    return switch (value) {
      case "limit" -> LIMIT;
      case "market" -> MARKET;
      default -> throw new IllegalArgumentException("unknown order type: " + value);
    };
  }
}
