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

  public void testBlockTradeDecodesEveryFieldAndOptionalNulls() {
    ByteBuffer buffer = validBlockTrade(16 + 78);

    BlockTradeDecoder.validate(buffer, 0);

    assertEquals(94, 16 + BlockTradeDecoder.BLOCK_LENGTH);
    assertEquals(11L, BlockTradeDecoder.matchId(buffer, 0));
    assertEquals(12L, BlockTradeDecoder.instrumentId(buffer, 0));
    assertEquals(13L, BlockTradeDecoder.blockTradeId(buffer, 0));
    assertEquals(14L, BlockTradeDecoder.blockRfqId(buffer, 0));
    assertFalse(BlockTradeDecoder.isBlockRfqIdNull(buffer, 0));
    assertEquals(15L, BlockTradeDecoder.fillQuantityMantissa(buffer, 0));
    assertEquals(-16L, BlockTradeDecoder.fillPriceMantissa(buffer, 0));
    assertEquals(17L, BlockTradeDecoder.markPriceMantissa(buffer, 0));
    assertEquals(18L, BlockTradeDecoder.indexPriceMantissa(buffer, 0));
    assertEquals(0.65d, BlockTradeDecoder.impliedVolatility(buffer, 0));
    assertFalse(BlockTradeDecoder.isImpliedVolatilityNull(buffer, 0));
    assertEquals(3L, BlockTradeDecoder.takerFlags(buffer, 0));
    assertTrue(BlockTradeDecoder.isSell(buffer, 0));
    assertTrue(BlockTradeDecoder.isLiquidation(buffer, 0));
    assertEquals(65_535, BlockTradeDecoder.numberOfLegs(buffer, 0));

    buffer.putLong(16 + 24, Long.MIN_VALUE);
    buffer.putDouble(16 + 64, Double.NaN);
    BlockTradeDecoder.validate(buffer, 0);
    assertTrue(BlockTradeDecoder.isBlockRfqIdNull(buffer, 0));
    assertTrue(BlockTradeDecoder.isImpliedVolatilityNull(buffer, 0));

    BlockTradeDecoder.validate(validBlockTrade(96), 0);
  }

  public void testBlockTradeRejectsCorruptLengthsPaddingFlagsAndRequiredNulls() {
    assertThrows(
        StarbaseProtocolException.class,
        () -> BlockTradeDecoder.validate(message(16 + 77, 33), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> BlockTradeDecoder.validate(validBlockTrade(16 + 79), 0));
    ByteBuffer dirtyPadding = validBlockTrade(96);
    dirtyPadding.put(95, (byte) 1);
    assertThrows(
        StarbaseProtocolException.class, () -> BlockTradeDecoder.validate(dirtyPadding, 0));

    ByteBuffer unknownFlags = validBlockTrade(16 + 78);
    unknownFlags.putInt(16 + 72, 4);
    assertThrows(
        StarbaseProtocolException.class, () -> BlockTradeDecoder.validate(unknownFlags, 0));

    int[] requiredOffsets = {0, 8, 16, 32, 40, 48, 56};
    for (int offset : requiredOffsets) {
      ByteBuffer nullRequired = validBlockTrade(16 + 78);
      nullRequired.putLong(16 + offset, Long.MIN_VALUE);
      assertThrows(
          StarbaseProtocolException.class, () -> BlockTradeDecoder.validate(nullRequired, 0));
    }

    ByteBuffer infiniteVolatility = validBlockTrade(16 + 78);
    infiniteVolatility.putDouble(16 + 64, Double.POSITIVE_INFINITY);
    assertThrows(
        StarbaseProtocolException.class,
        () -> BlockTradeDecoder.validate(infiniteVolatility, 0));
  }

  public void testValidTradeDecodeHotPathAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ByteBuffer summary = validSummary();
    ByteBuffer trade = validTrade();
    ByteBuffer blockTrade = validBlockTrade(16 + 78);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(summary, trade, blockTrade);
    }

    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(summary, trade, blockTrade);
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

  private static ByteBuffer validBlockTrade(int length) {
    ByteBuffer buffer = message(length, 33);
    buffer.putLong(16, 11);
    buffer.putLong(24, 12);
    buffer.putLong(32, 13);
    buffer.putLong(40, 14);
    buffer.putLong(48, 15);
    buffer.putLong(56, -16);
    buffer.putLong(64, 17);
    buffer.putLong(72, 18);
    buffer.putDouble(80, 0.65d);
    buffer.putInt(88, 3);
    buffer.putShort(92, (short) 65_535);
    return buffer;
  }

  private static ByteBuffer message(int length, int templateId) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(0, (short) length);
    buffer.putShort(2, (short) templateId);
    buffer.putShort(4, (short) 1);
    return buffer;
  }

  private static void exercise(ByteBuffer summary, ByteBuffer trade, ByteBuffer blockTrade) {
    TradeSummaryDecoder.validate(summary, 0);
    TradeDecoder.validate(trade, 0);
    BlockTradeDecoder.validate(blockTrade, 0);
    sink += TradeSummaryDecoder.tradeCount(summary, 0);
    sink += TradeDecoder.fillPriceMantissa(trade, 0);
    sink += BlockTradeDecoder.blockTradeId(blockTrade, 0);
    sink += (long) BlockTradeDecoder.impliedVolatility(blockTrade, 0);
  }
}
