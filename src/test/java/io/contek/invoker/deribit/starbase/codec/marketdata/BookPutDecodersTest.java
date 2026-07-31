package io.contek.invoker.deribit.starbase.codec.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class BookPutDecodersTest {

  private static volatile long sink;

  public void testBidAndAskDecodeAllExactBookIdentityAndPriorityFields() {
    ByteBuffer bid = putMessage(20, Long.MAX_VALUE - 1, 22, 33, 44, 55);
    BidPutDecoder.validate(bid, 0);
    assertEquals(Long.MAX_VALUE - 1, BidPutDecoder.orderId(bid, 0));
    assertEquals(22, BidPutDecoder.instrumentId(bid, 0));
    assertEquals(33, BidPutDecoder.quantityMantissa(bid, 0));
    assertEquals(44, BidPutDecoder.priceMantissa(bid, 0));
    assertEquals(55, BidPutDecoder.sortOrderId(bid, 0));
    assertEquals(1, BidPutDecoder.SIDE);

    ByteBuffer ask = putMessage(21, 66, 77, 88, 99, 111);
    AskPutDecoder.validate(ask, 0);
    assertEquals(66, AskPutDecoder.orderId(ask, 0));
    assertEquals(77, AskPutDecoder.instrumentId(ask, 0));
    assertEquals(88, AskPutDecoder.quantityMantissa(ask, 0));
    assertEquals(99, AskPutDecoder.priceMantissa(ask, 0));
    assertEquals(111, AskPutDecoder.sortOrderId(ask, 0));
    assertEquals(-1, AskPutDecoder.SIDE);
  }

  public void testPutDecodersRejectWrongSideTemplateTruncationAndRequiredNulls() {
    ByteBuffer bid = putMessage(20, 1, 2, 3, 4, 5);
    assertThrows(StarbaseProtocolException.class, () -> AskPutDecoder.validate(bid, 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> BidPutDecoder.validate(message(16 + 39, 20), 0));

    assertThrows(
        StarbaseProtocolException.class,
        () -> BidPutDecoder.validate(putMessage(20, Long.MIN_VALUE, 2, 3, 4, 5), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> BidPutDecoder.validate(putMessage(20, 1, 2, Long.MIN_VALUE, 4, 5), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> BidPutDecoder.validate(putMessage(20, 1, 2, 3, Long.MIN_VALUE, 5), 0));
  }

  public void testValidBookPutHotPathAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ByteBuffer bid = putMessage(20, 1, 2, 3, 4, 5);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(bid);
    }

    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(bid);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated, "valid book-put decoder hot path allocated bytes");
  }

  private static ByteBuffer putMessage(
      int templateId,
      long orderId,
      long instrumentId,
      long quantity,
      long price,
      long sortOrderId) {
    ByteBuffer buffer = message(16 + 40, templateId);
    buffer.putLong(16, orderId);
    buffer.putLong(24, instrumentId);
    buffer.putLong(32, quantity);
    buffer.putLong(40, price);
    buffer.putLong(48, sortOrderId);
    return buffer;
  }

  private static ByteBuffer message(int length, int templateId) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(0, (short) length);
    buffer.putShort(2, (short) templateId);
    buffer.putShort(4, (short) 1);
    return buffer;
  }

  private static void exercise(ByteBuffer bid) {
    BidPutDecoder.validate(bid, 0);
    sink += BidPutDecoder.orderId(bid, 0);
    sink += BidPutDecoder.priceMantissa(bid, 0);
  }
}
