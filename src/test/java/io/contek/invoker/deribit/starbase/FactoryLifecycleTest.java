package io.contek.invoker.deribit.starbase;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertNotSame;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertSame;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.NanoClock;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.common.StarbaseCredentials;
import io.contek.invoker.deribit.starbase.marketdata.StarbaseMarketDataApi;
import io.contek.invoker.deribit.starbase.marketdata.StarbaseMarketDataContext;
import io.contek.invoker.deribit.starbase.orderentry.StarbaseOrderEntryApi;
import io.contek.invoker.deribit.starbase.orderentry.StarbaseOrderEntryContext;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestApi;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestContext;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestCredentials;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;

public final class FactoryLifecycleTest {

  private static final NanoClock CLOCK = () -> 1L;
  public void testFactoryUsesSuppliedContextsAndApisCacheStableChannels() {
    StarbaseApiFactory factory = new StarbaseApiFactory();
    StarbaseMarketDataContext marketContext = marketContext();
    StarbaseOrderEntryContext orderContext = orderContext();
    StarbaseRestContext restContext = restContext();
    try (StarbaseCredentials credentials =
            new StarbaseCredentials("client".toCharArray(), "secret".toCharArray());
        StarbaseRestCredentials restCredentials =
            new StarbaseRestCredentials("rest-api-key".toCharArray())) {
      StarbaseMarketDataApi marketApi = factory.marketData(marketContext);
      StarbaseOrderEntryApi orderApi = factory.orderEntry(orderContext, credentials);
      StarbaseRestApi restApi = factory.rest(restContext, restCredentials);

      assertSame(marketContext, marketApi.context());
      assertSame(orderContext, orderApi.context());
      assertSame(restContext, restApi.context());
      assertSame(marketApi.getOrderBookChannel(42L), marketApi.getOrderBookChannel(42L));
      assertSame(marketApi.getTradesChannel(42L), marketApi.getTradesChannel(42L));
      assertNotSame(marketApi.getOrderBookChannel(42L), marketApi.getTradesChannel(42L));
      assertSame(orderApi.getOrderEventsChannel(), orderApi.getOrderEventsChannel());
      assertSame(orderApi.getFillsChannel(), orderApi.getFillsChannel());
      assertSame(orderApi.getSessionEventsChannel(), orderApi.getSessionEventsChannel());
    }
  }
  public void testLifecycleIsExplicitIdempotentAndNeverDrivenByListenerCount() {
    StarbaseMarketDataApi marketApi = new StarbaseApiFactory().marketData(marketContext());
    var channel = marketApi.getOrderBookChannel(7L);
    var subscription = channel.addListener((key, value, timestamp) -> {});

    assertFalse(marketApi.isStarted());
    marketApi.start();
    marketApi.start();
    assertTrue(marketApi.isStarted());

    subscription.close();
    assertTrue(marketApi.isStarted(), "removing the last listener must not close the API");

    marketApi.close();
    marketApi.close();
    assertTrue(marketApi.isClosed());
    assertFalse(marketApi.isStarted());
    assertThrows(IllegalStateException.class, marketApi::start);
  }
  public void testOrderEntryStartsUnauthenticatedUntilTransportStateMachineExists() {
    try (StarbaseCredentials credentials =
        new StarbaseCredentials("client".toCharArray(), "secret".toCharArray())) {
      StarbaseOrderEntryApi api = new StarbaseApiFactory().orderEntry(orderContext(), credentials);

      api.start();

      assertTrue(api.isStarted());
      assertFalse(api.isAuthenticated());
      api.close();
    }
  }

  private static StarbaseMarketDataContext marketContext() {
    return new StarbaseMarketDataContext(
        ProductGroup.BTC,
        GatewaySide.A,
        "loopback",
        new InetSocketAddress("239.1.1.1", 4220),
        new InetSocketAddress("239.1.1.2", 4230),
        new InetSocketAddress("127.0.0.1", 4240),
        4096,
        4096,
        Duration.ofMillis(250),
        IoPolicy.BLOCKING,
        CLOCK);
  }

  private static StarbaseOrderEntryContext orderContext() {
    return new StarbaseOrderEntryContext(
        new InetSocketAddress("127.0.0.1", 4210),
        ProductGroup.BTC,
        GatewaySide.A,
        Duration.ofSeconds(1),
        Duration.ofSeconds(5),
        4096,
        4096,
        IoPolicy.BLOCKING,
        CLOCK);
  }

  private static StarbaseRestContext restContext() {
    return new StarbaseRestContext(
        URI.create("http://127.0.0.1:4410/"),
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        CLOCK);
  }
}
