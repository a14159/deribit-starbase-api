package io.contek.invoker.deribit.starbase.rest;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class StarbaseAdministrativeEndpointsTest {

  public void testInvokesExactAuthenticatedGetOperationsAndValidatesResults() throws Exception {
    List<String> requests = new ArrayList<>();
    try (TestServer fixture = server(exchange -> {
      requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
          + exchange.getRequestHeaders().getFirst("Authorization"));
      String path = exchange.getRequestURI().getPath();
      respond(exchange, 200, path.endsWith("cancel_all")
          ? "{\"jsonrpc\":\"2.0\",\"result\":42}"
          : "{\"jsonrpc\":\"2.0\",\"result\":\"ok\"}");
    }); StarbaseRestApi api = api(fixture.server())) {
      assertEquals(42L, api.cancelAll());
      api.lockPortfolio();
      api.unlockPortfolio();
    }

    assertEquals(List.of(
        "GET /api/v2/private/cancel_all Bearer admin-key",
        "GET /api/v2/private/lock_portfolio Bearer admin-key",
        "GET /api/v2/private/unlock_portfolio Bearer admin-key"), requests);
  }

  public void testRejectsFractionalNegativeOverflowAndUnexpectedOkResults() throws Exception {
    assertCancelInvalid("1.5");
    assertCancelInvalid("-1");
    assertCancelInvalid("9223372036854775808");
    assertOkInvalid("lock_portfolio", "\"locked\"");
    assertOkInvalid("unlock_portfolio", "null");
  }

  public void testPropagatesJsonRpcErrorsAndClosedLifecycle() throws Exception {
    String error = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":10050,"
        + "\"message\":\"portfolio locked\"}}";
    try (TestServer fixture = server(exchange -> respond(exchange, 500, error));
        StarbaseRestApi api = api(fixture.server())) {
      StarbaseRestException failure = assertThrows(StarbaseRestException.class, api::cancelAll);
      assertEquals(10050, failure.errorCode());
    }

    try (TestServer fixture = server(exchange -> respond(exchange, 200,
        "{\"jsonrpc\":\"2.0\",\"result\":\"ok\"}"))) {
      StarbaseRestApi api = api(fixture.server());
      api.close();
      assertThrows(IllegalStateException.class, api::lockPortfolio);
    }
  }

  private static void assertCancelInvalid(String result) throws Exception {
    try (TestServer fixture = server(exchange -> respond(exchange, 200,
        "{\"jsonrpc\":\"2.0\",\"result\":" + result + "}"));
        StarbaseRestApi api = api(fixture.server())) {
      assertThrows(StarbaseRestException.class, api::cancelAll);
    }
  }

  private static void assertOkInvalid(String operation, String result) throws Exception {
    try (TestServer fixture = server(exchange -> respond(exchange, 200,
        "{\"jsonrpc\":\"2.0\",\"result\":" + result + "}"));
        StarbaseRestApi api = api(fixture.server())) {
      if (operation.equals("lock_portfolio"))
        assertThrows(StarbaseRestException.class, api::lockPortfolio);
      else assertThrows(StarbaseRestException.class, api::unlockPortfolio);
    }
  }

  private static StarbaseRestApi api(HttpServer server) {
    return new StarbaseRestApi(new StarbaseRestContext(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
        Duration.ofSeconds(1), Duration.ofSeconds(1), System::nanoTime),
        new StarbaseRestCredentials("admin-key".toCharArray()));
  }

  private static TestServer server(Handler handler) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", handler::handle);
    server.start();
    return new TestServer(server);
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  @FunctionalInterface private interface Handler { void handle(HttpExchange exchange) throws IOException; }
  private record TestServer(HttpServer server) implements AutoCloseable {
    @Override public void close() { server.stop(0); }
  }
}
