package io.contek.invoker.deribit.starbase.rest;

import java.util.Arrays;
import java.util.Objects;

/** Mutable Starbase REST bearer-token holder with explicit secret destruction. */
public final class StarbaseRestCredentials implements AutoCloseable {

  private char[] apiKey;

  public StarbaseRestCredentials(char[] apiKey) {
    Objects.requireNonNull(apiKey, "apiKey");
    if (apiKey.length == 0) {
      throw new IllegalArgumentException("apiKey must not be empty");
    }
    this.apiKey = apiKey.clone();
  }

  public synchronized char[] copyApiKey() {
    checkAvailable();
    return apiKey.clone();
  }

  public synchronized boolean isDestroyed() {
    return apiKey == null;
  }

  @Override
  public synchronized void close() {
    if (apiKey != null) {
      Arrays.fill(apiKey, '\0');
      apiKey = null;
    }
  }

  private void checkAvailable() {
    if (apiKey == null) {
      throw new IllegalStateException("credentials have been destroyed");
    }
  }

  @Override
  public String toString() {
    return "StarbaseRestCredentials[REDACTED]";
  }
}
