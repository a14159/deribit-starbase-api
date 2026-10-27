package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertSame;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.channel.StarbaseBlockTradeChannel;
import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.marketdata.BlockTradeDecoder;
import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;

public final class BlockTradesChannelTest {

  private static final int START = MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION;
  private static final int END = MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION;
  private static final int CONTINUATION = 0;

  private static volatile long sink;

  public void testTransactionsPublishEveryExactFieldInFillOrder() {
    StarbaseMarketDataApi api = api();
    StarbaseBlockTradeChannel channel = api.getBlockTradesChannel(100L);
    assertSame(channel, api.getBlockTradesChannel(100L));
    long[][] observed = new long[3][14];
    double[] volatility = new double[3];
    int[] count = new int[1];
    channel.addListener(
        (matchId,
            instrumentId,
            blockTradeId,
            blockRfqId,
            fillQuantity,
            fillPrice,
            markPrice,
            indexPrice,
            impliedVolatility,
            takerFlags,
            numberOfLegs,
            fillIndex,
            lastFill,
            sequence,
            timestamp) -> {
          int row = count[0]++;
          long[] values = observed[row];
          values[0] = matchId;
          values[1] = instrumentId;
          values[2] = blockTradeId;
          values[3] = blockRfqId;
          values[4] = fillQuantity;
          values[5] = fillPrice;
          values[6] = markPrice;
          values[7] = indexPrice;
          values[8] = takerFlags;
          values[9] = numberOfLegs;
          values[10] = fillIndex;
          values[11] = lastFill ? 1L : 0L;
          values[12] = sequence;
          values[13] = timestamp;
          volatility[row] = impliedVolatility;
        });

    api.routeDecodedMessage(
        blockTrade(1L, 100L, 7L, 8L, 25L, 1_000L, START, 701L),
        0,
        BlockTradeDecoder.TEMPLATE_ID,
        10L);
    api.routeDecodedMessage(
        blockTrade(2L, 100L, 9L, 8L, 35L, 1_000L, END, 702L),
        0,
        BlockTradeDecoder.TEMPLATE_ID,
        11L);
    ByteBuffer standard = blockTrade(3L, 100L, 10L, Long.MIN_VALUE, 5L, -2_000L, START | END, 703L);
    standard.putDouble(16 + BlockTradeDecoder.IMPLIED_VOLATILITY_OFFSET, Double.NaN);
    api.routeDecodedMessage(standard, 0, BlockTradeDecoder.TEMPLATE_ID, 12L);

    assertEquals(3, count[0]);
    assertEquals(1L, observed[0][0]);
    assertEquals(100L, observed[0][1]);
    assertEquals(7L, observed[0][2]);
    assertEquals(8L, observed[0][3]);
    assertEquals(25L, observed[0][4]);
    assertEquals(1_000L, observed[0][5]);
    assertEquals(17L, observed[0][6]);
    assertEquals(18L, observed[0][7]);
    assertEquals(0.65d, volatility[0]);
    assertEquals(1L, observed[0][8]);
    assertEquals(2L, observed[0][9]);
    assertEquals(0L, observed[0][10]);
    assertEquals(0L, observed[0][11]);
    assertEquals(10L, observed[0][12]);
    assertEquals(701L, observed[0][13]);
    assertEquals(2L, observed[1][0]);
    assertEquals(9L, observed[1][2]);
    assertEquals(1L, observed[1][10]);
    assertEquals(1L, observed[1][11]);
    assertEquals(11L, observed[1][12]);
    assertEquals(3L, observed[2][0]);
    assertEquals(Long.MIN_VALUE, observed[2][3]);
    assertEquals(-2_000L, observed[2][5]);
    assertTrue(Double.isNaN(volatility[2]));
    assertEquals(0L, observed[2][10]);
    assertEquals(1L, observed[2][11]);
  }

  public void testFramingIsTrackedWithoutAChannelAndOnlyTheMatchingChannelPublishes() {
    StarbaseMarketDataApi api = api();
    int[] other = new int[1];
    api.getBlockTradesChannel(200L)
        .addListener(
            (matchId,
                instrumentId,
                blockTradeId,
                blockRfqId,
                fillQuantity,
                fillPrice,
                markPrice,
                indexPrice,
                impliedVolatility,
                takerFlags,
                numberOfLegs,
                fillIndex,
                lastFill,
                sequence,
                timestamp) -> other[0]++);

    api.routeDecodedMessage(
        blockTrade(1L, 100L, 1L, 1L, 1L, 1L, START, 1L), 0, BlockTradeDecoder.TEMPLATE_ID, 1L);
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            api.routeDecodedMessage(
                blockTrade(2L, 100L, 1L, 1L, 1L, 1L, START | END, 2L),
                0,
                BlockTradeDecoder.TEMPLATE_ID,
                2L));
    assertEquals(0, other[0]);
  }

  public void testAlignedFramingFailsClosedForOrphanNestedAndCrossInstrumentFills() {
    StarbaseMarketDataApi orphan = aligned();
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            orphan.routeDecodedMessage(
                blockTrade(2L, 100L, 1L, 1L, 1L, 1L, CONTINUATION, 2L),
                0,
                BlockTradeDecoder.TEMPLATE_ID,
                2L));

    StarbaseMarketDataApi nested = aligned();
    nested.routeDecodedMessage(
        blockTrade(2L, 100L, 1L, 1L, 1L, 1L, START, 2L), 0, BlockTradeDecoder.TEMPLATE_ID, 2L);
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            nested.routeDecodedMessage(
                blockTrade(3L, 100L, 1L, 1L, 1L, 1L, START, 3L),
                0,
                BlockTradeDecoder.TEMPLATE_ID,
                3L));

    StarbaseMarketDataApi crossInstrument = aligned();
    crossInstrument.routeDecodedMessage(
        blockTrade(2L, 100L, 1L, 1L, 1L, 1L, START, 2L), 0, BlockTradeDecoder.TEMPLATE_ID, 2L);
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            crossInstrument.routeDecodedMessage(
                blockTrade(3L, 200L, 1L, 1L, 1L, 1L, END, 3L),
                0,
                BlockTradeDecoder.TEMPLATE_ID,
                3L));
  }

  public void testJoiningMidTransactionSkipsOrphanFillsUntilAligned() {
    StarbaseMarketDataApi api = api();
    int[] count = new int[1];
    api.getBlockTradesChannel(100L)
        .addListener(
            (matchId,
                instrumentId,
                blockTradeId,
                blockRfqId,
                fillQuantity,
                fillPrice,
                markPrice,
                indexPrice,
                impliedVolatility,
                takerFlags,
                numberOfLegs,
                fillIndex,
                lastFill,
                sequence,
                timestamp) -> count[0]++);

    api.routeDecodedMessage(
        blockTrade(1L, 100L, 1L, 1L, 1L, 1L, CONTINUATION, 1L),
        0,
        BlockTradeDecoder.TEMPLATE_ID,
        1L);
    api.routeDecodedMessage(
        blockTrade(2L, 100L, 1L, 1L, 1L, 1L, END, 2L), 0, BlockTradeDecoder.TEMPLATE_ID, 2L);
    assertEquals(0, count[0]);

    api.routeDecodedMessage(
        blockTrade(3L, 100L, 1L, 1L, 1L, 1L, START | END, 3L),
        0,
        BlockTradeDecoder.TEMPLATE_ID,
        3L);
    assertEquals(1, count[0]);
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            api.routeDecodedMessage(
                blockTrade(4L, 100L, 1L, 1L, 1L, 1L, CONTINUATION, 4L),
                0,
                BlockTradeDecoder.TEMPLATE_ID,
                4L));
  }

  public void testBlockTradeDispatchAllocatesNothingAfterWarmup() {
    StarbaseMarketDataApi api = api();
    api.getBlockTradesChannel(100L)
        .addListener(
            (matchId,
                instrumentId,
                blockTradeId,
                blockRfqId,
                fillQuantity,
                fillPrice,
                markPrice,
                indexPrice,
                impliedVolatility,
                takerFlags,
                numberOfLegs,
                fillIndex,
                lastFill,
                sequence,
                timestamp) -> sink += matchId + fillPrice + fillIndex + sequence);
    ByteBuffer first = blockTrade(1L, 100L, 1L, 1L, 1L, 1L, START, 1L);
    ByteBuffer last = blockTrade(2L, 100L, 1L, 1L, 1L, 1L, END, 2L);
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      api.routeDecodedMessage(first, 0, BlockTradeDecoder.TEMPLATE_ID, 1L);
      api.routeDecodedMessage(last, 0, BlockTradeDecoder.TEMPLATE_ID, 2L);
    }
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      api.routeDecodedMessage(first, 0, BlockTradeDecoder.TEMPLATE_ID, 1L);
      api.routeDecodedMessage(last, 0, BlockTradeDecoder.TEMPLATE_ID, 2L);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated);
  }

  private static StarbaseMarketDataApi aligned() {
    StarbaseMarketDataApi api = api();
    api.routeDecodedMessage(
        blockTrade(1L, 100L, 1L, 1L, 1L, 1L, START | END, 1L),
        0,
        BlockTradeDecoder.TEMPLATE_ID,
        1L);
    return api;
  }

  private static ByteBuffer blockTrade(
      long match,
      long instrument,
      long blockTradeId,
      long blockRfqId,
      long quantity,
      long price,
      int flags,
      long timestamp) {
    int length = 16 + BlockTradeDecoder.BLOCK_LENGTH;
    ByteBuffer value = ByteBuffer.allocateDirect(length).order(ByteOrder.LITTLE_ENDIAN);
    value.putShort(0, (short) length);
    value.putShort(2, (short) BlockTradeDecoder.TEMPLATE_ID);
    value.putShort(4, (short) 1);
    value.putShort(6, (short) flags);
    value.putLong(8, timestamp);
    value.putLong(16, match);
    value.putLong(24, instrument);
    value.putLong(32, blockTradeId);
    value.putLong(40, blockRfqId);
    value.putLong(48, quantity);
    value.putLong(56, price);
    value.putLong(64, 17L);
    value.putLong(72, 18L);
    value.putDouble(80, 0.65d);
    value.putInt(88, 1);
    value.putShort(92, (short) 2);
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
