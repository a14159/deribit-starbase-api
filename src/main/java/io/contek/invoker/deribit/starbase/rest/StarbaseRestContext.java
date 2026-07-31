package io.contek.invoker.deribit.starbase.rest;

import static io.contek.invoker.deribit.starbase.common.Configuration.positive;
import static io.contek.invoker.deribit.starbase.common.Configuration.required;

import io.contek.invoker.deribit.starbase.common.NanoClock;
import java.net.URI;
import java.time.Duration;

public record StarbaseRestContext(
    URI baseUri, Duration connectTimeout, Duration requestTimeout, NanoClock clock) {

  public StarbaseRestContext {
    baseUri = required(baseUri, "baseUri");
    String scheme = baseUri.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw new IllegalArgumentException("baseUri scheme must be http or https");
    }
    if (baseUri.getHost() == null) {
      throw new IllegalArgumentException("baseUri must have a host");
    }
    connectTimeout = positive(connectTimeout, "connectTimeout");
    requestTimeout = positive(requestTimeout, "requestTimeout");
    clock = required(clock, "clock");
  }
}
