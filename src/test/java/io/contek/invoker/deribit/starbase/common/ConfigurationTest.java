package io.contek.invoker.deribit.starbase.common;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertArrayEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.marketdata.StarbaseMarketDataContext;
import io.contek.invoker.deribit.starbase.orderentry.StarbaseOrderEntryContext;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestContext;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestCredentials;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;

public final class ConfigurationTest {

  private static final NanoClock CLOCK = () -> 123L;
  public void testCredentialsAreDefensivelyCopiedAndExplicitlyDestroyed() {
    char[] username = "client".toCharArray();
    char[] password = "secret".toCharArray();
    StarbaseCredentials credentials = new StarbaseCredentials(username, password);
    username[0] = 'X';
    password[0] = 'X';

    assertArrayEquals("client".toCharArray(), credentials.copyUsername());
    assertArrayEquals("secret".toCharArray(), credentials.copyPassword());
    assertFalse(credentials.isDestroyed());

    credentials.close();

    assertTrue(credentials.isDestroyed());
    assertThrows(IllegalStateException.class, credentials::copyPassword);
  }
  public void testRestCredentialsAreSeparateDefensiveAndRedacted() {
    char[] key = "rest-secret".toCharArray();
    StarbaseRestCredentials credentials = new StarbaseRestCredentials(key);
    key[0] = 'X';

    assertArrayEquals("rest-secret".toCharArray(), credentials.copyApiKey());
    assertFalse(credentials.toString().contains("rest-secret"));
    credentials.close();
    assertTrue(credentials.isDestroyed());
    assertThrows(IllegalStateException.class, credentials::copyApiKey);
  }
  public void testOrderEntryContextRetainsExplicitRoutingAndTransportConfiguration() {
    InetSocketAddress endpoint = new InetSocketAddress("127.0.0.1", 4210);
    StarbaseOrderEntryContext context =
        new StarbaseOrderEntryContext(
            endpoint,
            ProductGroup.BTC,
            GatewaySide.A,
            Duration.ofSeconds(2),
            Duration.ofSeconds(5),
            64 * 1024,
            32 * 1024,
            IoPolicy.SPIN,
            CLOCK);

    assertEquals(endpoint, context.endpoint());
    assertEquals(ProductGroup.BTC, context.productGroup());
    assertEquals(GatewaySide.A, context.gatewaySide());
    assertEquals(123L, context.clock().nanoTime());
  }
  public void testMarketDataContextRequiresAllConfiguredEndpointsAndInterface() {
    StarbaseMarketDataContext context =
        new StarbaseMarketDataContext(
            ProductGroup.ETH,
            GatewaySide.B,
            "loopback",
            new InetSocketAddress("239.1.1.1", 4220),
            new InetSocketAddress("239.1.1.2", 4230),
            new InetSocketAddress("127.0.0.1", 4240),
            128 * 1024,
            8 * 1024,
            Duration.ofMillis(250),
            IoPolicy.BLOCKING,
            CLOCK);

    assertEquals(ProductGroup.ETH, context.productGroup());
    assertEquals(GatewaySide.B, context.gatewaySide());
    assertEquals("loopback", context.networkInterfaceName());
  }
  public void testRestContextAllowsConfiguredHttpForLoopbackTestsAndHttpsForDeployment() {
    StarbaseRestContext context =
        new StarbaseRestContext(
            URI.create("http://127.0.0.1:4410/"),
            Duration.ofSeconds(2),
            Duration.ofSeconds(3),
            CLOCK);

    assertEquals("http", context.baseUri().getScheme());
    assertEquals(123L, context.clock().nanoTime());
  }
  public void testInvalidConfigurationFailsAtConstructionBoundary() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StarbaseCredentials(new char[0], "secret".toCharArray()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StarbaseCredentials("client".toCharArray(), new char[49]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StarbaseOrderEntryContext(
                new InetSocketAddress("127.0.0.1", 4210),
                ProductGroup.BTC,
                GatewaySide.A,
                Duration.ZERO,
                Duration.ofSeconds(1),
                31,
                32,
                IoPolicy.BLOCKING,
                CLOCK));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StarbaseMarketDataContext(
                ProductGroup.BTC,
                GatewaySide.A,
                " ",
                new InetSocketAddress("239.1.1.1", 4220),
                new InetSocketAddress("239.1.1.2", 4230),
                new InetSocketAddress("127.0.0.1", 4240),
                24,
                24,
                Duration.ofMillis(1),
                IoPolicy.SPIN,
                CLOCK));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StarbaseRestContext(
                URI.create("ftp://example.test/"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                CLOCK));
  }
  public void testProtocolExceptionHierarchyPreservesFailureCategory() {
    StarbaseException exception =
        new StarbaseProtocolException("corrupt frame", new IllegalArgumentException("length"));

    assertEquals("corrupt frame", exception.getMessage());
    assertTrue(exception.getCause() instanceof IllegalArgumentException);
  }
}
