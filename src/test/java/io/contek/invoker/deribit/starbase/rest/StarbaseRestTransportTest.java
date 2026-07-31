package io.contek.invoker.deribit.starbase.rest;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.contek.invoker.deribit.starbase.common.NanoClock;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public final class StarbaseRestTransportTest {

  private static final NanoClock CLOCK = () -> 123L;

  public void testResolvesConfiguredBasePathAndAddsBearerOnlyToPrivateRequests() throws Exception {
    AtomicReference<String> path = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    try (TestServer fixture = server(exchange -> {
      path.set(exchange.getRequestURI().toString());
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      respond(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"ok\":true}}");
    })) {
      HttpServer server = fixture.server();
      StarbaseRestCredentials credentials =
          new StarbaseRestCredentials("top-secret-api-key".toCharArray());
      StarbaseRestTransport transport =
          new StarbaseRestTransport(context(server, "/gateway/"), credentials);

      StarbaseRestResponse response =
          transport.get("api/v2/private/get_open_orders?currency=BTC", true);

      assertEquals("/gateway/api/v2/private/get_open_orders?currency=BTC", path.get());
      assertEquals("Bearer top-secret-api-key", authorization.get());
      assertEquals(200, response.httpStatus());
      assertEquals("{\"ok\":true}", response.resultJson());

      transport.get("api/v2/public/get_instruments", false);
      assertEquals(null, authorization.get());
      credentials.close();
      assertTrue(credentials.isDestroyed());
    }
  }

  public void testPropagatesRequestTimeoutWithoutExposingCredentials() throws Exception {
    String secret = "never-print-this";
    try (TestServer fixture = server(exchange -> {
      try {
        Thread.sleep(250L);
        respond(exchange, 200, "{\"jsonrpc\":\"2.0\",\"result\":[]}");
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    })) {
      HttpServer server = fixture.server();
      StarbaseRestContext context =
          new StarbaseRestContext(
              baseUri(server, "/"), Duration.ofSeconds(1), Duration.ofMillis(25), CLOCK);
      StarbaseRestTransport transport =
          new StarbaseRestTransport(context, new StarbaseRestCredentials(secret.toCharArray()));

      StarbaseRestException failure =
          assertThrows(
              StarbaseRestException.class,
              () -> transport.get("api/v2/private/get_open_orders", true));

      assertTrue(failure.isTimeout());
      assertFalse(failure.toString().contains(secret));
      assertFalse(transport.toString().contains(secret));
      assertThrows(
          IllegalArgumentException.class,
          () -> transport.get("https://attacker.invalid/api/v2/private/get_open_orders", true));
    }
  }

  public void testParsesStructuredJsonRpcErrorsAndPreservesUnknownData() throws Exception {
    String body =
        "{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-32042,"
            + "\"message\":\"rate limited\",\"data\":{\"retry_after_ms\":60000}}}";
    try (TestServer fixture = server(exchange -> respond(exchange, 429, body))) {
      HttpServer server = fixture.server();
      StarbaseRestTransport transport =
          new StarbaseRestTransport(
              context(server, "/"), new StarbaseRestCredentials("key".toCharArray()));

      StarbaseRestException failure =
          assertThrows(
              StarbaseRestException.class,
              () -> transport.get("api/v2/private/get_open_orders", true));

      assertEquals(429, failure.httpStatus());
      assertEquals(-32042, failure.errorCode());
      assertEquals("rate limited", failure.getMessage());
      assertEquals("{\"retry_after_ms\":60000}", failure.dataJson());
      assertFalse(failure.isTimeout());
    }
  }

  public void testRejectsMalformedSuccessAndNonJsonHttpErrors() throws Exception {
    try (TestServer fixture = server(exchange -> respond(exchange, 200, "{\"jsonrpc\":\"2.0\"}"))) {
      HttpServer server = fixture.server();
      StarbaseRestTransport transport =
          new StarbaseRestTransport(
              context(server, "/"), new StarbaseRestCredentials("key".toCharArray()));
      assertThrows(
          StarbaseRestException.class,
          () -> transport.get("api/v2/public/get_instruments", false));
    }

    try (TestServer fixture = server(exchange -> respond(exchange, 503, "upstream unavailable"))) {
      HttpServer server = fixture.server();
      StarbaseRestTransport transport =
          new StarbaseRestTransport(
              context(server, "/"), new StarbaseRestCredentials("key".toCharArray()));
      StarbaseRestException failure =
          assertThrows(
              StarbaseRestException.class,
              () -> transport.get("api/v2/private/get_open_orders", true));
      assertEquals(503, failure.httpStatus());
      assertEquals(StarbaseRestException.NO_ERROR_CODE, failure.errorCode());
    }
  }

  private static StarbaseRestContext context(HttpServer server, String path) {
    return new StarbaseRestContext(
        baseUri(server, path), Duration.ofSeconds(1), Duration.ofSeconds(1), CLOCK);
  }

  private static URI baseUri(HttpServer server, String path) {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
  }

  private static TestServer server(Handler handler) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> handler.handle(exchange));
    server.start();
    return new TestServer(server);
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  @FunctionalInterface
  private interface Handler {
    void handle(HttpExchange exchange) throws IOException;
  }

  private record TestServer(HttpServer server) implements AutoCloseable {
    @Override
    public void close() {
      server.stop(0);
    }
  }
}
