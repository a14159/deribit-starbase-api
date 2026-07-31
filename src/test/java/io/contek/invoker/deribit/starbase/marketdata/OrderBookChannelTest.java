package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidPutDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.AskPutDecoder;
import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import java.net.InetSocketAddress;
import java.time.Duration;

public final class OrderBookChannelTest {

  public void testPublishesOnlyCoherentChangedLevelsAfterSnapshotReadiness() {
    StarbaseMarketDataApi api = api();
    api.configureOrderBook(100L, 8, 8);
    long[] event = new long[4];
    api.getOrderBookChannel(100L)
        .addListener(
            (price, signedQuantity, timestamp) -> {
              event[0]++;
              event[1] = price;
              event[2] = signedQuantity;
              event[3] = timestamp;
            });

    api.onBookPut(
        1L,
        100L,
        BidPutDecoder.SIDE,
        10L,
        -500L,
        1L,
        MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION,
        10L,
        100L,
        false);
    assertEquals(0L, event[0]);
    api.markOrderBookSnapshotComplete(100L);
    assertFalse(api.isOrderBookReady(100L), "open transaction remains unready");

    api.onBookPut(
        2L,
        100L,
        BidPutDecoder.SIDE,
        20L,
        -500L,
        2L,
        MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION,
        11L,
        101L,
        false);

    assertTrue(api.isOrderBookReady(100L));
    assertEquals(1L, event[0]);
    assertEquals(-500L, event[1]);
    assertEquals(30L, event[2]);
    assertEquals(101L, event[3]);
  }

  public void testRemovalAndInvalidationAreExplicitAndListenerLifetimeIsStable() {
    StarbaseMarketDataApi api = api();
    api.configureOrderBook(100L, 4, 4);
    long[] count = new long[1];
    var subscription =
        api.getOrderBookChannel(100L)
            .addListener((price, quantity, timestamp) -> count[0]++);
    api.markOrderBookSnapshotComplete(100L);
    api.onBookPut(1L, 100L, BidPutDecoder.SIDE, 1L, 10L, 1L, 3, 1L, 1L, false);
    api.onBookDelete(1L, 100L, BidPutDecoder.SIDE, 3, 2L, 2L, false);
    api.invalidateOrderBook(100L, 3L);

    assertEquals(3L, count[0]);
    assertFalse(api.isOrderBookReady(100L));
    subscription.close();
    api.markOrderBookSnapshotComplete(100L);
    assertEquals(0, api.getOrderBookChannel(100L).listenerCount());
  }

  public void testAskReductionAndPriceMovePublishExactSignedChangedLevels() {
    StarbaseMarketDataApi api = api();
    api.configureOrderBook(100L, 8, 8);
    long[] prices = new long[4];
    long[] quantities = new long[4];
    int[] count = new int[1];
    api.getOrderBookChannel(100L)
        .addListener(
            (price, quantity, timestamp) -> {
              prices[count[0]] = price;
              quantities[count[0]] = quantity;
              count[0]++;
            });
    api.markOrderBookSnapshotComplete(100L);

    api.onBookPut(1L, 100L, AskPutDecoder.SIDE, 10L, -100L, 1L, 3, 1L, 1L, false);
    api.onBookReduce(1L, 100L, AskPutDecoder.SIDE, 4L, 3, 2L, 2L, false);
    api.onBookPut(1L, 100L, AskPutDecoder.SIDE, 3L, -90L, 2L, 3, 3L, 3L, false);

    assertEquals(4, count[0]);
    assertEquals(-100L, prices[0]);
    assertEquals(-10L, quantities[0]);
    assertEquals(-4L, quantities[1]);
    assertEquals(-100L, prices[2]);
    assertEquals(0L, quantities[2]);
    assertEquals(-90L, prices[3]);
    assertEquals(-3L, quantities[3]);
  }

  public void testPendingCycleIsUnreadyAndProtocolFailureClearsState() {
    StarbaseMarketDataApi api = api();
    api.configureOrderBook(100L, 4, 4);
    long[] invalidations = new long[1];
    api.getOrderBookChannel(100L)
        .addListener(
            (price, quantity, timestamp) -> {
              if (price == Long.MIN_VALUE) {
                invalidations[0]++;
              }
            });
    api.markOrderBookSnapshotComplete(100L);
    api.onBookPut(1L, 100L, BidPutDecoder.SIDE, 1L, 10L, 1L, 0, 1L, 1L, false);
    assertFalse(api.isOrderBookReady(100L));
    api.onBookPut(1L, 100L, BidPutDecoder.SIDE, 1L, 10L, 1L, 0, 2L, 2L, true);
    assertTrue(api.isOrderBookReady(100L));

    io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows(
        RuntimeException.class,
        () ->
            api.onBookDelete(
                404L, 100L, BidPutDecoder.SIDE, 3, 3L, 3L, false));
    assertFalse(api.isOrderBookReady(100L));
    assertEquals(1L, invalidations[0]);
  }

  private static StarbaseMarketDataApi api() {
    StarbaseMarketDataApi api =
        new StarbaseMarketDataApi(
            new StarbaseMarketDataContext(
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
                () -> 1L),
            4);
    api.instrumentRegistry()
        .upsert(
            100L,
            "BTC-PERPETUAL",
            ProductGroup.BTC,
            "BTC",
            "USD",
            -8,
            1L,
            1L,
            0,
            0,
            1);
    return api;
  }
}
