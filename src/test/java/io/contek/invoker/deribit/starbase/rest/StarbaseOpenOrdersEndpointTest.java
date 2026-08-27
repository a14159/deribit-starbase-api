package io.contek.invoker.deribit.starbase.rest;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertNull;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class StarbaseOpenOrdersEndpointTest {

  public void testDecodesCurrentPortfolioScopedGoldenSnapshotWithBearerAuthentication() throws Exception {
    String body = """
        {"jsonrpc":"2.0","id":1,"result":[{
          "order_id":"215074398825086978",
          "instrument_name":"TREE-USD","side":"sell","price":0.0717,
          "amount":83698,"filled_amount":0.125,"average_price":0.07165,
          "order_state":"open","order_type":"limit","time_in_force":"GTC",
          "post_only":true,"reject_post_only":false,"reduce_only":false,
          "creation_timestamp":1778270370643,
          "last_update_timestamp":1778270370644,"label":"hedge-1","api":true,
          "max_show":1000,"profit_loss":-1.25,"commission":0.00042,
          "future_rollout_field":{"ignored":true}
        }]}
        """;
    AtomicReference<String> uri = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    try (TestServer fixture = server(exchange -> {
      uri.set(exchange.getRequestURI().toString());
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      respond(exchange, 200, body);
    }); StarbaseRestApi api = api(fixture.server())) {
      List<StarbaseOpenOrder> result = api.getOpenOrders();

      assertEquals("/api/v2/private/get_open_orders", uri.get());
      assertEquals("Bearer portfolio-key", authorization.get());
      assertEquals(1, result.size());
      StarbaseOpenOrder order = result.getFirst();
      assertEquals(215_074_398_825_086_978L, order.orderId());
      assertEquals("TREE-USD", order.instrumentName());
      assertEquals(StarbaseOrderSide.SELL, order.side());
      assertEquals(new BigDecimal("0.0717"), order.price());
      assertEquals(new BigDecimal("83698"), order.amount());
      assertEquals(new BigDecimal("0.125"), order.filledAmount());
      assertEquals(StarbaseRestOrderState.OPEN, order.state());
      assertEquals(StarbaseRestOrderType.LIMIT, order.type());
      assertEquals(StarbaseTimeInForce.GTC, order.timeInForce());
      assertTrue(order.postOnly());
      assertFalse(order.rejectPostOnly());
      assertFalse(order.reduceOnly());
      assertEquals(1_778_270_370_643L, order.creationTimestamp());
      assertEquals(new BigDecimal("-1.25"), order.profitLoss());
    }
  }

  public void testSupportsEmptySnapshotAndNullableOptionalFields() throws Exception {
    try (TestServer fixture = server(exchange ->
        respond(exchange, 200, "{\"jsonrpc\":\"2.0\",\"result\":[]}"));
        StarbaseRestApi api = api(fixture.server())) {
      assertTrue(api.getOpenOrders().isEmpty());
    }

    String order = """
        {"order_id":"-9223372036854775807","instrument_name":"BTC-PERPETUAL","side":"buy",
        "price":0,"amount":1,"filled_amount":0,"order_state":"open",
        "order_type":"market","time_in_force":null,"post_only":null,
        "reduce_only":null,"label":null}
        """;
    try (TestServer fixture = server(exchange -> respond(exchange, 200, envelope(order)));
        StarbaseRestApi api = api(fixture.server())) {
      StarbaseOpenOrder decoded = api.getOpenOrders().getFirst();
      assertEquals(Long.MIN_VALUE + 1, decoded.orderId());
      assertEquals(StarbaseRestOrderType.MARKET, decoded.type());
      assertNull(decoded.timeInForce());
      assertNull(decoded.postOnly());
      assertNull(decoded.creationTimestamp());
    }
  }

  public void testParsesExactSignedLongBoundariesAndLongParseLongForms() throws Exception {
    String orders = validOrderId(Long.toString(Long.MAX_VALUE)) + ","
        + validOrderId(Long.toString(Long.MIN_VALUE + 1)) + ","
        + validOrderId("+17") + "," + validOrderId("00018");
    try (TestServer fixture = server(exchange -> respond(exchange, 200, envelopeValues(orders)));
        StarbaseRestApi api = api(fixture.server())) {
      List<StarbaseOpenOrder> decoded = api.getOpenOrders();
      assertEquals(Long.MAX_VALUE, decoded.get(0).orderId());
      assertEquals(Long.MIN_VALUE + 1, decoded.get(1).orderId());
      assertEquals(17L, decoded.get(2).orderId());
      assertEquals(18L, decoded.get(3).orderId());
    }
  }

  public void testMalformedOverflowSentinelUuidAndDuplicateIdentitiesFailClosed()
      throws Exception {
    String[] invalid = {
      "", " 1", "1 ", "1.0", "9223372036854775808", "-9223372036854775809",
      "-9223372036854775808", "1cc1c718-49e0-4ea5-8902-f3f22968c350"
    };
    for (String orderId : invalid) {
      assertInvalid(validOrderId(orderId));
    }

    assertInvalidValues(validOrderId("1") + "," + validOrderId("+1"));
  }

  public void testInvalidRequiredFieldsAndEnumsFailAsStructuredResponseErrors() throws Exception {
    assertInvalid("{\"instrument_name\":\"BTC-PERPETUAL\",\"side\":\"buy\","
        + "\"price\":1,\"amount\":1,\"filled_amount\":0,\"order_state\":\"open\","
        + "\"order_type\":\"limit\"}");
    assertInvalid(validOrder("hold", "open", "limit", "GTC"));
    assertInvalid(validOrder("buy", "filled", "limit", "GTC"));
    assertInvalid(validOrder("buy", "open", "stop", "GTC"));
    assertInvalid(validOrder("buy", "open", "limit", "DAY"));
  }

  public void testPropagatesAuthenticatedJsonRpcFailures() throws Exception {
    String error = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":13009,"
        + "\"message\":\"invalid token\",\"data\":{\"scope\":\"portfolio\"}}}";
    try (TestServer fixture = server(exchange -> respond(exchange, 401, error));
        StarbaseRestApi api = api(fixture.server())) {
      StarbaseRestException failure = assertThrows(StarbaseRestException.class, api::getOpenOrders);
      assertEquals(401, failure.httpStatus());
      assertEquals(13009, failure.errorCode());
      assertEquals("{\"scope\":\"portfolio\"}", failure.dataJson());
    }
  }

  private static String validOrder(String side, String state, String type, String tif) {
    return "{\"order_id\":\"7\",\"instrument_name\":\"BTC-PERPETUAL\",\"side\":\""
        + side + "\",\"price\":1,\"amount\":1,\"filled_amount\":0,"
        + "\"order_state\":\"" + state + "\",\"order_type\":\"" + type
        + "\",\"time_in_force\":\"" + tif + "\"}";
  }

  private static String validOrderId(String orderId) {
    return "{\"order_id\":\"" + orderId + "\",\"instrument_name\":\"BTC-PERPETUAL\","
        + "\"side\":\"buy\",\"price\":1,\"amount\":1,\"filled_amount\":0,"
        + "\"order_state\":\"open\",\"order_type\":\"limit\"}";
  }

  private static void assertInvalid(String order) throws Exception {
    try (TestServer fixture = server(exchange -> respond(exchange, 200, envelope(order)));
        StarbaseRestApi api = api(fixture.server())) {
      assertThrows(StarbaseRestException.class, api::getOpenOrders);
    }
  }

  private static void assertInvalidValues(String orders) throws Exception {
    try (TestServer fixture = server(exchange -> respond(exchange, 200, envelopeValues(orders)));
        StarbaseRestApi api = api(fixture.server())) {
      assertThrows(StarbaseRestException.class, api::getOpenOrders);
    }
  }

  private static String envelope(String order) {
    return "{\"jsonrpc\":\"2.0\",\"result\":[" + order + "]}";
  }

  private static String envelopeValues(String orders) {
    return "{\"jsonrpc\":\"2.0\",\"result\":[" + orders + "]}";
  }

  private static StarbaseRestApi api(HttpServer server) {
    StarbaseRestContext context = new StarbaseRestContext(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
        Duration.ofSeconds(1), Duration.ofSeconds(1), System::nanoTime);
    return new StarbaseRestApi(context,
        new StarbaseRestCredentials("portfolio-key".toCharArray()));
  }

  private static TestServer server(Handler handler) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", handler::handle);
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

  @FunctionalInterface private interface Handler { void handle(HttpExchange exchange) throws IOException; }
  private record TestServer(HttpServer server) implements AutoCloseable {
    @Override public void close() { server.stop(0); }
  }
}
