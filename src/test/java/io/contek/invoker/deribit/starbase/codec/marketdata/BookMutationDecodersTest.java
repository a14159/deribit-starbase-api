package io.contek.invoker.deribit.starbase.codec.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class BookMutationDecodersTest {

  private static volatile long sink;

  public void testBidAndAskQuantityReductionDecodeExactRemainingQuantity() {
    ByteBuffer bid = reducedMessage(22, Long.MAX_VALUE - 1, 101, 202);
    BidQtyReducedDecoder.validate(bid, 0);
    assertEquals(Long.MAX_VALUE - 1, BidQtyReducedDecoder.orderId(bid, 0));
    assertEquals(101, BidQtyReducedDecoder.instrumentId(bid, 0));
    assertEquals(202, BidQtyReducedDecoder.quantityMantissa(bid, 0));
    assertEquals(1, BidQtyReducedDecoder.SIDE);

    ByteBuffer ask = reducedMessage(23, 303, 404, 505);
    AskQtyReducedDecoder.validate(ask, 0);
    assertEquals(303, AskQtyReducedDecoder.orderId(ask, 0));
    assertEquals(404, AskQtyReducedDecoder.instrumentId(ask, 0));
    assertEquals(505, AskQtyReducedDecoder.quantityMantissa(ask, 0));
    assertEquals(-1, AskQtyReducedDecoder.SIDE);
  }

  public void testBidAndAskDeleteDecodeExactOrderIdentity() {
    ByteBuffer bid = deleteMessage(24, Long.MAX_VALUE, 606);
    BidDeleteDecoder.validate(bid, 0);
    assertEquals(Long.MAX_VALUE, BidDeleteDecoder.orderId(bid, 0));
    assertEquals(606, BidDeleteDecoder.instrumentId(bid, 0));
    assertEquals(1, BidDeleteDecoder.SIDE);

    ByteBuffer ask = deleteMessage(25, 707, 808);
    AskDeleteDecoder.validate(ask, 0);
    assertEquals(707, AskDeleteDecoder.orderId(ask, 0));
    assertEquals(808, AskDeleteDecoder.instrumentId(ask, 0));
    assertEquals(-1, AskDeleteDecoder.SIDE);
  }

  public void testMutationDecodersRejectWrongSideLengthsAndRequiredNulls() {
    ByteBuffer bidReduced = reducedMessage(22, 1, 2, 3);
    assertThrows(
        StarbaseProtocolException.class, () -> AskQtyReducedDecoder.validate(bidReduced, 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> BidQtyReducedDecoder.validate(message(16 + 23, 22), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> BidDeleteDecoder.validate(message(16 + 15, 24), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> BidQtyReducedDecoder.validate(reducedMessage(22, Long.MIN_VALUE, 2, 3), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> BidQtyReducedDecoder.validate(reducedMessage(22, 1, 2, Long.MIN_VALUE), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> AskDeleteDecoder.validate(deleteMessage(25, 1, Long.MIN_VALUE), 0));
  }

  public void testValidBookMutationHotPathAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ByteBuffer reduced = reducedMessage(22, 1, 2, 3);
    ByteBuffer deleted = deleteMessage(25, 4, 5);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(reduced, deleted);
    }

    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(reduced, deleted);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated, "valid book mutation decoder hot path allocated bytes");
  }

  private static ByteBuffer reducedMessage(
      int templateId, long orderId, long instrumentId, long quantityMantissa) {
    ByteBuffer buffer = message(16 + 24, templateId);
    buffer.putLong(16, orderId);
    buffer.putLong(24, instrumentId);
    buffer.putLong(32, quantityMantissa);
    return buffer;
  }

  private static ByteBuffer deleteMessage(int templateId, long orderId, long instrumentId) {
    ByteBuffer buffer = message(16 + 16, templateId);
    buffer.putLong(16, orderId);
    buffer.putLong(24, instrumentId);
    return buffer;
  }

  private static ByteBuffer message(int length, int templateId) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(0, (short) length);
    buffer.putShort(2, (short) templateId);
    buffer.putShort(4, (short) 1);
    return buffer;
  }

  private static void exercise(ByteBuffer reduced, ByteBuffer deleted) {
    BidQtyReducedDecoder.validate(reduced, 0);
    AskDeleteDecoder.validate(deleted, 0);
    sink += BidQtyReducedDecoder.quantityMantissa(reduced, 0);
    sink += AskDeleteDecoder.orderId(deleted, 0);
  }
}
