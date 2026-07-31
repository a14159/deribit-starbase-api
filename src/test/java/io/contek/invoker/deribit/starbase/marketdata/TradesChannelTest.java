package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertSame;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.channel.StarbaseTradeChannel;
import io.contek.invoker.deribit.starbase.codec.marketdata.TradeDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.TradeSummaryDecoder;
import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.net.InetSocketAddress;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;

public final class TradesChannelTest {

  private static volatile long sink;

  public void testSummaryScopesOrderedTradeCallbacksWithEveryExactField() {
    StarbaseMarketDataApi api = api();
    StarbaseTradeChannel channel = api.getTradesChannel(100L);
    assertSame(channel, api.getTradesChannel(100L));
    long[][] observed = new long[2][16];
    int[] count = new int[1];
    channel.addListener(
        (matchId,
            instrumentId,
            makerOrderId,
            fillQuantity,
            fillPrice,
            makerFlags,
            takerOrderId,
            totalFilled,
            deepestPrice,
            markPrice,
            indexPrice,
            takerFlags,
            tradeIndex,
            tradeCount,
            sequence,
            timestamp) -> {
          long[] row = observed[count[0]++];
          row[0] = matchId;
          row[1] = instrumentId;
          row[2] = makerOrderId;
          row[3] = fillQuantity;
          row[4] = fillPrice;
          row[5] = makerFlags;
          row[6] = takerOrderId;
          row[7] = totalFilled;
          row[8] = deepestPrice;
          row[9] = markPrice;
          row[10] = indexPrice;
          row[11] = takerFlags;
          row[12] = tradeIndex;
          row[13] = tradeCount;
          row[14] = sequence;
          row[15] = timestamp;
        });

    api.routeDecodedMessage(summary(100L, 9L, 30L, 40L, 50L, 60L, 2, 3L, 700L),
        0, TradeSummaryDecoder.TEMPLATE_ID, 10L);
    api.routeDecodedMessage(trade(1L, 100L, Long.MIN_VALUE, 11L, -90L, 1L, 701L),
        0, TradeDecoder.TEMPLATE_ID, 11L);
    api.routeDecodedMessage(trade(2L, 100L, 8L, 19L, -80L, 2L, 702L),
        0, TradeDecoder.TEMPLATE_ID, 12L);

    assertEquals(2, count[0]);
    assertEquals(1L, observed[0][0]);
    assertEquals(Long.MIN_VALUE, observed[0][2]);
    assertEquals(9L, observed[0][6]);
    assertEquals(3L, observed[0][11]);
    assertEquals(0L, observed[0][12]);
    assertEquals(2L, observed[1][0]);
    assertEquals(1L, observed[1][12]);
    assertEquals(2L, observed[1][13]);
    assertEquals(12L, observed[1][14]);
    assertEquals(702L, observed[1][15]);
  }

  public void testMalformedSummaryLifecycleFailsClosed() {
    StarbaseMarketDataApi stray = api();
    stray.getTradesChannel(100L);
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            stray.routeDecodedMessage(
                trade(1L, 100L, 2L, 3L, 4L, 0L, 1L),
                0,
                TradeDecoder.TEMPLATE_ID,
                1L));

    StarbaseMarketDataApi nested = api();
    nested.getTradesChannel(100L);
    nested.routeDecodedMessage(
        summary(100L, 1L, 1L, 1L, 1L, 1L, 2, 0L, 2L),
        0,
        TradeSummaryDecoder.TEMPLATE_ID,
        2L);
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            nested.routeDecodedMessage(
                summary(100L, 1L, 1L, 1L, 1L, 1L, 1, 0L, 3L),
                0,
                TradeSummaryDecoder.TEMPLATE_ID,
                3L));

    StarbaseMarketDataApi incomplete = api();
    incomplete.routeDecodedMessage(
        summary(100L, 1L, 1L, 1L, 1L, 1L, 1, 0L, 4L),
        0,
        TradeSummaryDecoder.TEMPLATE_ID,
        4L);
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            incomplete.routeDecodedMessage(
                message(20, 119, 5L), 0, 119, 5L));
  }

  public void testSummaryAndTradeDispatchAllocateNothingAfterWarmup() {
    StarbaseMarketDataApi api = api();
    api.getTradesChannel(100L)
        .addListener(
            (matchId,
                instrumentId,
                makerOrderId,
                fillQuantity,
                fillPrice,
                makerFlags,
                takerOrderId,
                totalFilled,
                deepestPrice,
                markPrice,
                indexPrice,
                takerFlags,
                tradeIndex,
                tradeCount,
                sequence,
                timestamp) -> sink += matchId + fillPrice + sequence);
    ByteBuffer summary = summary(100L, 1L, 1L, 1L, 1L, 1L, 1, 0L, 1L);
    ByteBuffer trade = trade(1L, 100L, 2L, 3L, 4L, 0L, 2L);
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      api.routeDecodedMessage(summary, 0, TradeSummaryDecoder.TEMPLATE_ID, 1L);
      api.routeDecodedMessage(trade, 0, TradeDecoder.TEMPLATE_ID, 2L);
    }
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      api.routeDecodedMessage(summary, 0, TradeSummaryDecoder.TEMPLATE_ID, 1L);
      api.routeDecodedMessage(trade, 0, TradeDecoder.TEMPLATE_ID, 2L);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated);
  }

  private static ByteBuffer summary(
      long instrument,
      long taker,
      long total,
      long deepest,
      long mark,
      long index,
      int count,
      long flags,
      long timestamp) {
    ByteBuffer value = message(72, TradeSummaryDecoder.TEMPLATE_ID, timestamp);
    value.putLong(16, instrument);
    value.putLong(24, taker);
    value.putLong(32, total);
    value.putLong(40, deepest);
    value.putLong(48, mark);
    value.putLong(56, index);
    value.putInt(64, count);
    value.putInt(68, (int) flags);
    return value;
  }

  private static ByteBuffer trade(
      long match,
      long instrument,
      long maker,
      long quantity,
      long price,
      long flags,
      long timestamp) {
    ByteBuffer value = message(60, TradeDecoder.TEMPLATE_ID, timestamp);
    value.putLong(16, match);
    value.putLong(24, instrument);
    value.putLong(32, maker);
    value.putLong(40, quantity);
    value.putLong(48, price);
    value.putInt(56, (int) flags);
    return value;
  }

  private static ByteBuffer message(int length, int templateId, long timestamp) {
    ByteBuffer value = ByteBuffer.allocateDirect(length).order(ByteOrder.LITTLE_ENDIAN);
    value.putShort(0, (short) length);
    value.putShort(2, (short) templateId);
    value.putShort(4, (short) 1);
    value.putLong(8, timestamp);
    return value;
  }

  private static StarbaseMarketDataApi api() {
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
        4);
  }
}
