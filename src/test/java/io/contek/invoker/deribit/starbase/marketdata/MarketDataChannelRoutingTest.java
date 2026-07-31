package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertNotSame;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertSame;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import io.contek.invoker.deribit.starbase.channel.StarbaseSubscription;
import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.codec.marketdata.InstrumentStatusUpdateDecoder;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;

public final class MarketDataChannelRoutingTest {

  public void testCachesByExactSignedLongIdAndSeparatesStreamKinds() {
    StarbaseMarketDataApi api = api(4);

    assertSame(
        api.getOrderBookChannel(Long.MAX_VALUE),
        api.getOrderBookChannel(Long.MAX_VALUE));
    assertNotSame(
        api.getOrderBookChannel(Long.MAX_VALUE),
        api.getOrderBookChannel(-1L));
    assertNotSame(
        api.getOrderBookChannel(Long.MAX_VALUE),
        api.getTradesChannel(Long.MAX_VALUE));
  }

  public void testPrimitiveDispatchRoutesOnlyToTheExactInstrument() {
    StarbaseMarketDataApi api = api(4);
    long[] values = new long[3];
    StarbaseSubscription first =
        api.getOrderBookChannel(1L)
            .addListener((key, value, timestamp) -> values[0] += key + value + timestamp);
    api.getOrderBookChannel(2L)
        .addListener((key, value, timestamp) -> values[1] += key + value + timestamp);
    api.getReferenceDataChannel()
        .addListener((key, value, timestamp) -> values[2] += key + value + timestamp);

    api.publishOrderBook(1L, 10L, 100L);
    api.publishReferenceData(2L, 3L, 200L);

    assertEquals(111L, values[0]);
    assertEquals(0L, values[1]);
    assertEquals(205L, values[2]);
    first.close();
    api.publishOrderBook(1L, 10L, 100L);
    assertEquals(111L, values[0]);
  }

  public void testFixedRoutingCapacityFailsWithoutLosingCachedChannels() {
    StarbaseMarketDataApi api = api(1);
    var channel = api.getOrderBookChannel(1L);

    assertThrows(IllegalStateException.class, () -> api.getOrderBookChannel(2L));
    assertSame(channel, api.getOrderBookChannel(1L));
  }

  public void testDecodedReferenceStatusUpdatesRegistryBeforePublishing() {
    StarbaseMarketDataApi api = api(2);
    api.instrumentRegistry()
        .upsert(
            7L,
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
    long[] observed = new long[3];
    api.getReferenceDataChannel()
        .addListener(
            (key, value, timestamp) -> {
              observed[0] = key;
              observed[1] = value;
              observed[2] = timestamp;
              assertEquals(5, api.instrumentRegistry().status(key));
            });
    ByteBuffer message =
        ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN);
    message.putShort(0, (short) 25);
    message.putShort(2, (short) InstrumentStatusUpdateDecoder.TEMPLATE_ID);
    message.putShort(4, (short) 1);
    message.putLong(8, 999L);
    message.putLong(16, 7L);
    message.put(24, (byte) 5);

    api.routeDecodedMessage(
        message, 0, InstrumentStatusUpdateDecoder.TEMPLATE_ID, 10L);

    assertEquals(7L, observed[0]);
    assertEquals(InstrumentStatusUpdateDecoder.TEMPLATE_ID, observed[1]);
    assertEquals(999L, observed[2]);
  }

  private static StarbaseMarketDataApi api(int capacity) {
    return new StarbaseMarketDataApi(
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
        capacity);
  }
}
