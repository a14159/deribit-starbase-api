package io.contek.invoker.deribit.starbase.common;

import java.util.Arrays;
import java.util.Objects;

/** Mutable secret holder whose internal copies can be explicitly erased. */
public final class StarbaseCredentials implements AutoCloseable {

  private char[] username;
  private char[] password;

  public StarbaseCredentials(char[] username, char[] password) {
    this.username = validatedCopy(username, 16, "username");
    this.password = validatedCopy(password, 48, "password");
  }

  public synchronized char[] copyUsername() {
    checkAvailable();
    return username.clone();
  }

  public synchronized char[] copyPassword() {
    checkAvailable();
    return password.clone();
  }

  public synchronized boolean isDestroyed() {
    return username == null;
  }

  @Override
  public synchronized void close() {
    if (username != null) {
      Arrays.fill(username, '\0');
      Arrays.fill(password, '\0');
      username = null;
      password = null;
    }
  }

  private void checkAvailable() {
    if (username == null) {
      throw new IllegalStateException("credentials have been destroyed");
    }
  }

  private static char[] validatedCopy(char[] value, int maximumLength, String name) {
    Objects.requireNonNull(value, name);
    if (value.length == 0 || value.length > maximumLength) {
      throw new IllegalArgumentException(name + " length must be 1.." + maximumLength);
    }
    return value.clone();
  }

  @Override
  public String toString() {
    return "StarbaseCredentials[REDACTED]";
  }
}
