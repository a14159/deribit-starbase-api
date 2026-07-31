package io.contek.invoker.deribit.starbase.rest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Bounded synchronous HTTP transport for the non-hot-path Starbase utility API. */
public final class StarbaseRestTransport implements AutoCloseable {

  private final StarbaseRestContext context;
  private final HttpClient client;
  private char[] apiKey;

  public StarbaseRestTransport(StarbaseRestContext context, StarbaseRestCredentials credentials) {
    this.context = Objects.requireNonNull(context, "context");
    Objects.requireNonNull(credentials, "credentials");
    apiKey = credentials.copyApiKey();
    client = HttpClient.newBuilder().connectTimeout(context.connectTimeout()).build();
  }

  public synchronized StarbaseRestResponse get(String relativePath, boolean authenticated) {
    if (apiKey == null) throw new IllegalStateException("REST transport is closed");
    URI requested = URI.create(Objects.requireNonNull(relativePath, "relativePath"));
    if (requested.isAbsolute()
        || requested.getRawAuthority() != null
        || requested.getRawPath().startsWith("/")
        || requested.normalize().getRawPath().startsWith("../")) {
      throw new IllegalArgumentException("relativePath must remain under the configured REST base URI");
    }
    URI uri = context.baseUri().resolve(requested);
    if (!sameAuthority(context.baseUri(), uri)) {
      throw new IllegalArgumentException("relativePath must remain under the configured REST authority");
    }
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri)
            .timeout(context.requestTimeout())
            .header("Accept", "application/json")
            .GET();
    if (authenticated) builder.header("Authorization", "Bearer " + new String(apiKey));
    try {
      HttpResponse<String> response =
          client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return parse(response.statusCode(), response.body());
    } catch (HttpTimeoutException timeout) {
      throw failure("Starbase REST request timed out", 0, true, timeout);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw failure("Starbase REST request interrupted", 0, false, interrupted);
    } catch (IOException io) {
      throw failure("Starbase REST transport failed", 0, false, io);
    }
  }

  private static StarbaseRestResponse parse(int status, String body) {
    String jsonRpc = JsonEnvelope.stringValue(JsonEnvelope.member(body, "jsonrpc"));
    String result = JsonEnvelope.member(body, "result");
    if (status >= 200 && status < 300 && "2.0".equals(jsonRpc) && result != null) {
      return new StarbaseRestResponse(status, result);
    }
    String error = JsonEnvelope.member(body, "error");
    if ("2.0".equals(jsonRpc) && error != null) {
      String codeJson = JsonEnvelope.member(error, "code");
      String message = JsonEnvelope.stringValue(JsonEnvelope.member(error, "message"));
      try {
        int code = Integer.parseInt(codeJson);
        if (message != null) {
          throw new StarbaseRestException(
              message, status, code, JsonEnvelope.member(error, "data"), false, null);
        }
      } catch (NumberFormatException ignored) {
        // Fall through to a generic secret-safe protocol failure.
      }
    }
    throw failure("Invalid Starbase REST response", status, false, null);
  }

  private static StarbaseRestException failure(
      String message, int status, boolean timeout, Throwable cause) {
    return new StarbaseRestException(
        message, status, StarbaseRestException.NO_ERROR_CODE, null, timeout, cause);
  }

  private static boolean sameAuthority(URI base, URI resolved) {
    return base.getScheme().equalsIgnoreCase(resolved.getScheme())
        && Objects.equals(base.getHost(), resolved.getHost())
        && base.getPort() == resolved.getPort();
  }

  @Override
  public synchronized void close() {
    if (apiKey != null) {
      Arrays.fill(apiKey, '\0');
      apiKey = null;
    }
  }

  @Override
  public String toString() {
    return "StarbaseRestTransport[baseUri=" + context.baseUri() + ", credentials=REDACTED]";
  }
}
