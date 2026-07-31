package io.contek.invoker.deribit.starbase.common;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;

public final class Configuration {

  public static <T> T required(T value, String name) {
    return Objects.requireNonNull(value, name);
  }

  public static InetSocketAddress endpoint(InetSocketAddress value, String name) {
    required(value, name);
    if (value.getPort() < 1 || value.getPort() > 65_535) {
      throw new IllegalArgumentException(name + " must have a valid port");
    }
    return value;
  }

  public static Duration positive(Duration value, String name) {
    required(value, name);
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  public static int minimum(int value, int minimum, String name) {
    if (value < minimum) {
      throw new IllegalArgumentException(name + " must be at least " + minimum);
    }
    return value;
  }

  public static String nonBlank(String value, String name) {
    required(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  private Configuration() {}
}
