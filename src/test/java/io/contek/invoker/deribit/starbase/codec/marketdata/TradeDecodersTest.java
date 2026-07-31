package io.contek.invoker.deribit.starbase.codec.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class TradeDecodersTest {

  private static volatile long sink;

  public void testTradeSummaryDecodesCompleteAggressorContextAndCount() {
    ByteBuffer buffer = message(16 + 56, 30);
    buffer.putLong(16, 1);
    buffer.putLong(24, 2);
    buffer.putLong(32, 3);
    buffer.putLong(40, 4);
    buffer.putLong(48, 5);
    buffer.putLong(56, 6);
    buffer.putInt(64, 7);
    buffer.putInt(68, 3);

    TradeSummaryDecoder.validate(buffer, 0);

    assertEquals(1, TradeSummaryDecoder.instrumentId(buffer, 0));
    assertEquals(2, TradeSummaryDecoder.takerOrderId(buffer, 0));
    assertEquals(3, TradeSummaryDecoder.totalFilledMantissa(buffer, 0));
    assertEquals(4, TradeSummaryDecoder.deepestPriceMantissa(buffer, 0));
    assertEquals(5, TradeSummaryDecoder.markPriceMantissa(buffer, 0));
    assertEquals(6, TradeSummaryDecoder.indexPriceMantissa(buffer, 0));
    assertEquals(7, TradeSummaryDecoder.tradeCount(buffer, 0));
    assertEquals(3, TradeSummaryDecoder.takerFlags(buffer, 0));
    assertTrue(TradeSummaryDecoder.isSell(buffer, 0));
    assertTrue(TradeSummaryDecoder.isLiquidation(buffer, 0));
  }

  public void testTradeDecodesFillAndPreservesOptionalMakerNull() {
    ByteBuffer buffer = message(16 + 44, 31);
    buffer.putLong(16, Long.MAX_VALUE);
    buffer.putLong(24, 20);
    buffer.putLong(32, Long.MIN_VALUE);
    buffer.putLong(40, 30);
    buffer.putLong(48, 40);
    buffer.putInt(56, 1);

    TradeDecoder.validate(buffer, 0);

    assertEquals(Long.MAX_VALUE, TradeDecoder.matchId(buffer, 0));
    assertEquals(20, TradeDecoder.instrumentId(buffer, 0));
    assertTrue(TradeDecoder.isMakerOrderIdNull(buffer, 0));
    assertEquals(30, TradeDecoder.fillQuantityMantissa(buffer, 0));
    assertEquals(40, TradeDecoder.fillPriceMantissa(buffer, 0));
    assertEquals(1, TradeDecoder.makerFlags(buffer, 0));
    assertTrue(TradeDecoder.isSell(buffer, 0));
    assertFalse(TradeDecoder.isLiquidation(buffer, 0));
  }

  public void testTradeDecodersRejectCorruptLengthsCountsFlagsAndRequiredNulls() {
    assertThrows(
        StarbaseProtocolException.class,
        () -> TradeSummaryDecoder.validate(message(16 + 55, 30), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> TradeDecoder.validate(message(16 + 44, 30), 0));

    ByteBuffer negativeCount = validSummary();
    negativeCount.putInt(64, -1);
    assertThrows(
        StarbaseProtocolException.class,
        () -> TradeSummaryDecoder.validate(negativeCount, 0));

    ByteBuffer unknownFlags = validTrade();
    unknownFlags.putInt(56, 4);
    assertThrows(
        StarbaseProtocolException.class, () -> TradeDecoder.validate(unknownFlags, 0));

    ByteBuffer nullFill = validTrade();
    nullFill.putLong(40, Long.MIN_VALUE);
    assertThrows(
        StarbaseProtocolException.class, () -> TradeDecoder.validate(nullFill, 0));
  }

  public void testValidTradeDecodeHotPathAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ByteBuffer summary = validSummary();
    ByteBuffer trade = validTrade();
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(summary, trade);
    }

    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(summary, trade);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated, "valid trade decoder hot path allocated bytes");
  }

  private static ByteBuffer validSummary() {
    ByteBuffer buffer = message(16 + 56, 30);
    for (int index = 0; index < 6; index++) {
      buffer.putLong(16 + index * 8, index + 1);
    }
    buffer.putInt(64, 1);
    return buffer;
  }

  private static ByteBuffer validTrade() {
    ByteBuffer buffer = message(16 + 44, 31);
    for (int index = 0; index < 5; index++) {
      buffer.putLong(16 + index * 8, index + 1);
    }
    return buffer;
  }

  private static ByteBuffer message(int length, int templateId) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(0, (short) length);
    buffer.putShort(2, (short) templateId);
    buffer.putShort(4, (short) 1);
    return buffer;
  }

  private static void exercise(ByteBuffer summary, ByteBuffer trade) {
    TradeSummaryDecoder.validate(summary, 0);
    TradeDecoder.validate(trade, 0);
    sink += TradeSummaryDecoder.tradeCount(summary, 0);
    sink += TradeDecoder.fillPriceMantissa(trade, 0);
  }
}
