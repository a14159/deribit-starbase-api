package io.contek.invoker.deribit.starbase.rest;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertNull;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.contek.invoker.deribit.starbase.book.InstrumentRegistry;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class StarbaseInstrumentsEndpointTest {

  public void testDecodesCurrentGoldenResponseAndBootstrapsExactRegistryIdentity() throws Exception {
    String body = """
        {"jsonrpc":"2.0","id":1,"result":[
          {"instrument_id":5000000123,"instrument_name":"ETH-PERPETUAL",
           "kind":"perp_future","index_id":77,"product_group":"ETH",
           "base_currency":"ETH","quote_currency":"USDC","settlement_currency":"USDC",
           "tick_size":0.01,"qty_tick_size":0.001,"is_active":true,"creation_timestamp":1747500000000,
           "min_trade_amount":0.001,"contract_size":1,"maker_commission":-0.0001},
          {"instrument_id":200001,"instrument_name":"BTC-30MAY26-70000-C",
           "kind":"option","product_group":"BTC","base_currency":"BTC",
           "quote_currency":"USDC","settlement_currency":"USDC","tick_size":0.5,
           "strike":70000,"option_type":"call","is_active":false,
           "expiration_timestamp":1779148800000,"unknown_rollout_field":{"safe":true}}
        ]}
        """;
    AtomicReference<String> requestUri = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    try (TestServer fixture = server(exchange -> {
      requestUri.set(exchange.getRequestURI().toString());
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      respond(exchange, 200, body);
    }); StarbaseRestApi api = api(fixture.server())) {
      InstrumentRegistry registry = new InstrumentRegistry(4);

      List<StarbaseInstrument> instruments =
          api.getInstruments(
              new StarbaseInstrumentFilter("BTC & ETH", StarbaseInstrumentKind.OPTION, false),
              registry);

      assertEquals(
          "/api/v2/public/get_instruments?currency=BTC%20%26%20ETH&kind=option&expired=false",
          requestUri.get());
      assertNull(authorization.get());
      assertEquals(2, instruments.size());
      StarbaseInstrument perpetual = instruments.getFirst();
      assertEquals(5_000_000_123L, perpetual.instrumentId());
      assertEquals("ETH-PERPETUAL", perpetual.instrumentName());
      assertEquals(StarbaseInstrumentKind.PERP_FUTURE, perpetual.kind());
      assertEquals(77L, perpetual.indexId());
      assertEquals(new BigDecimal("0.01"), perpetual.tickSize());
      assertEquals(new BigDecimal("0.001"), perpetual.quantityTickSize());
      assertEquals(new BigDecimal("-0.0001"), perpetual.makerCommission());
      assertTrue(perpetual.active());
      assertEquals(5_000_000_123L, registry.instrumentId("ETH-PERPETUAL"));
      assertEquals(ProductGroup.ETH, registry.productGroup(5_000_000_123L));
      assertFalse(registry.hasAuthoritativeDefinition(5_000_000_123L));
      assertEquals(ProductGroup.BTC, registry.productGroup(200_001L));
      assertFalse(instruments.get(1).active());
    }
  }

  public void testAcceptsNullableOptionalFieldsAndEmptyResults() throws Exception {
    String body = """
        {"jsonrpc":"2.0","result":[{"instrument_id":9,"instrument_name":"XRP_USDC",
        "kind":"spot","product_group":"TIER_3","base_currency":null,
        "quote_currency":null,"tick_size":null,"is_active":true,
        "expiration_timestamp":null}]}
        """;
    try (TestServer fixture = server(exchange -> respond(exchange, 200, body));
        StarbaseRestApi api = api(fixture.server())) {
      List<StarbaseInstrument> result = api.getInstruments(StarbaseInstrumentFilter.ALL, null);
      assertEquals(1, result.size());
      assertNull(result.getFirst().baseCurrency());
      assertNull(result.getFirst().tickSize());
    }

    try (TestServer fixture = server(exchange ->
        respond(exchange, 200, "{\"jsonrpc\":\"2.0\",\"result\":[]}"));
        StarbaseRestApi api = api(fixture.server())) {
      assertTrue(api.getInstruments(StarbaseInstrumentFilter.ALL, null).isEmpty());
    }
  }

  public void testRefreshUpdatesRestIdentityButAuthoritativeDefinitionWins() throws Exception {
    InstrumentRegistry registry = new InstrumentRegistry(1);
    registry.bootstrapIdentity(42L, "SOL-PERPETUAL", ProductGroup.TIER_3);
    String body = """
        {"jsonrpc":"2.0","result":[{"instrument_id":42,
        "instrument_name":"SOL-PERPETUAL","kind":"perp_future",
        "product_group":"TIER_2","is_active":true}]}
        """;
    try (TestServer fixture = server(exchange -> respond(exchange, 200, body));
        StarbaseRestApi api = api(fixture.server())) {
      api.getInstruments(StarbaseInstrumentFilter.ALL, registry);
    }
    assertEquals(ProductGroup.TIER_2, registry.productGroup(42L));

    registry.upsert(42L, "SOL-PERPETUAL", ProductGroup.TIER_2, "SOL", "USD", -4,
        10L, 1L, 0, 0, 0);
    registry.bootstrapIdentity(42L, "SOL-PERPETUAL", ProductGroup.TIER_3);
    assertEquals(ProductGroup.TIER_2, registry.productGroup(42L));
    assertTrue(registry.hasAuthoritativeDefinition(42L));
  }

  public void testMalformedRequiredFieldsKindsGroupsAndConflictingIdentityFailClosed() throws Exception {
    assertInvalid("{\"instrument_name\":\"BTC-PERPETUAL\",\"kind\":\"perp_future\","
        + "\"product_group\":\"BTC\",\"is_active\":true}");
    assertInvalid("{\"instrument_id\":1,\"instrument_name\":\"BTC-PERPETUAL\","
        + "\"kind\":\"future\",\"product_group\":\"BTC\",\"is_active\":true}");
    assertInvalid("{\"instrument_id\":1,\"instrument_name\":\"BTC-PERPETUAL\","
        + "\"kind\":\"perp_future\",\"product_group\":\"OTHER\",\"is_active\":true}");

    InstrumentRegistry registry = new InstrumentRegistry(1);
    registry.bootstrapIdentity(1L, "ONE", ProductGroup.BTC);
    String result = "{\"instrument_id\":1,\"instrument_name\":\"RENAMED\","
        + "\"kind\":\"perp_future\",\"product_group\":\"BTC\",\"is_active\":true}";
    try (TestServer fixture = server(exchange -> respond(exchange, 200, envelope(result)));
        StarbaseRestApi api = api(fixture.server())) {
      assertThrows(RuntimeException.class,
          () -> api.getInstruments(StarbaseInstrumentFilter.ALL, registry));
    }
    assertEquals("ONE", registry.name(1L));

    InstrumentRegistry atomic = new InstrumentRegistry(3);
    atomic.bootstrapIdentity(1L, "ONE", ProductGroup.BTC);
    String batch = "{\"jsonrpc\":\"2.0\",\"result\":["
        + "{\"instrument_id\":2,\"instrument_name\":\"TWO\",\"kind\":\"spot\","
        + "\"product_group\":\"TIER_2\",\"is_active\":true},"
        + "{\"instrument_id\":1,\"instrument_name\":\"CONFLICT\",\"kind\":\"spot\","
        + "\"product_group\":\"BTC\",\"is_active\":true}]}";
    try (TestServer fixture = server(exchange -> respond(exchange, 200, batch));
        StarbaseRestApi api = api(fixture.server())) {
      assertThrows(RuntimeException.class,
          () -> api.getInstruments(StarbaseInstrumentFilter.ALL, atomic));
    }
    assertFalse(atomic.contains(2L), "a failed refresh must not partially bootstrap identities");
  }

  private static void assertInvalid(String instrumentJson) throws Exception {
    try (TestServer fixture = server(exchange -> respond(exchange, 200, envelope(instrumentJson)));
        StarbaseRestApi api = api(fixture.server())) {
      assertThrows(StarbaseRestException.class,
          () -> api.getInstruments(StarbaseInstrumentFilter.ALL, null));
    }
  }

  private static String envelope(String instrumentJson) {
    return "{\"jsonrpc\":\"2.0\",\"result\":[" + instrumentJson + "]}";
  }

  private static StarbaseRestApi api(HttpServer server) {
    StarbaseRestContext context = new StarbaseRestContext(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
        Duration.ofSeconds(1), Duration.ofSeconds(1), System::nanoTime);
    return new StarbaseRestApi(context, new StarbaseRestCredentials("rest-key".toCharArray()));
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

  @FunctionalInterface
  private interface Handler {
    void handle(HttpExchange exchange) throws IOException;
  }

  private record TestServer(HttpServer server) implements AutoCloseable {
    @Override public void close() { server.stop(0); }
  }
}
