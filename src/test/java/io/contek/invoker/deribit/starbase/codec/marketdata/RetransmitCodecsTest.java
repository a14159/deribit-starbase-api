package io.contek.invoker.deribit.starbase.codec.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class RetransmitCodecsTest {

  private static volatile long sink;

  public void testRequestEncoderWritesExactAbsoluteLittleEndianLayoutAndMaximumCount() {
    ByteBuffer buffer = ByteBuffer.allocateDirect(40).order(ByteOrder.LITTLE_ENDIAN);
    buffer.position(39);

    RetransmitRequestEncoder.encode(buffer, 3, Long.MAX_VALUE, 255, 123_456);

    assertEquals(25, Short.toUnsignedInt(buffer.getShort(3)));
    assertEquals(200, Short.toUnsignedInt(buffer.getShort(5)));
    assertEquals(1, Short.toUnsignedInt(buffer.getShort(7)));
    assertEquals(0, Short.toUnsignedInt(buffer.getShort(9)));
    assertEquals(123_456, buffer.getLong(11));
    assertEquals(Long.MAX_VALUE, buffer.getLong(19));
    assertEquals(255, Byte.toUnsignedInt(buffer.get(27)));
    assertEquals(39, buffer.position());
  }

  public void testRequestEncoderRejectsZeroOverflowNegativeSequenceEndianAndBounds() {
    ByteBuffer buffer = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN);
    assertThrows(
        IllegalArgumentException.class,
        () -> RetransmitRequestEncoder.encode(buffer, 0, 1, 0, 2));
    assertThrows(
        IllegalArgumentException.class,
        () -> RetransmitRequestEncoder.encode(buffer, 0, 1, 256, 2));
    assertThrows(
        IllegalArgumentException.class,
        () -> RetransmitRequestEncoder.encode(buffer, 0, -1, 1, 2));
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            RetransmitRequestEncoder.encode(
                buffer.order(ByteOrder.BIG_ENDIAN), 0, 1, 1, 2));
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            RetransmitRequestEncoder.encode(
                ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN), 0, 1, 1, 2));
  }

  public void testRejectDecoderExposesRetryDetailsAndPinnedReason() {
    ByteBuffer buffer = rejectMessage(999, "rate limited", 3);

    RetransmitRejectDecoder.validate(buffer, 0);

    assertEquals(999, RetransmitRejectDecoder.retryDelayNanos(buffer, 0));
    assertEquals(12, RetransmitRejectDecoder.detailsLength(buffer, 0));
    assertEquals('r', RetransmitRejectDecoder.detailsByte(buffer, 0, 0));
    assertEquals('d', RetransmitRejectDecoder.detailsByte(buffer, 0, 11));
    assertEquals(3, RetransmitRejectDecoder.reason(buffer, 0));
  }

  public void testRejectDecoderRejectsWrongTemplateLengthDelayAndUnknownReason() {
    assertThrows(
        StarbaseProtocolException.class,
        () -> RetransmitRejectDecoder.validate(message(16 + 49, 200), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> RetransmitRejectDecoder.validate(message(16 + 48, 202), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> RetransmitRejectDecoder.validate(rejectMessage(-1, "bad", 1), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> RetransmitRejectDecoder.validate(rejectMessage(1, "bad", 5), 0));
  }

  public void testValidRetransmitCodecHotPathAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ByteBuffer request = ByteBuffer.allocateDirect(25).order(ByteOrder.LITTLE_ENDIAN);
    ByteBuffer reject = rejectMessage(1, "x", 4);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(request, reject, iteration);
    }

    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(request, reject, iteration);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated, "valid retransmit codec hot path allocated bytes");
  }

  private static ByteBuffer rejectMessage(long retryDelay, String details, int reason) {
    ByteBuffer buffer = message(16 + 49, 202);
    buffer.putLong(16, retryDelay);
    for (int index = 0; index < details.length(); index++) {
      buffer.put(24 + index, (byte) details.charAt(index));
    }
    buffer.put(64, (byte) reason);
    return buffer;
  }

  private static ByteBuffer message(int length, int templateId) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(0, (short) length);
    buffer.putShort(2, (short) templateId);
    buffer.putShort(4, (short) 1);
    return buffer;
  }

  private static void exercise(ByteBuffer request, ByteBuffer reject, int iteration) {
    RetransmitRequestEncoder.encode(request, 0, iteration, 255, iteration);
    RetransmitRejectDecoder.validate(reject, 0);
    sink += RetransmitRejectDecoder.retryDelayNanos(reject, 0);
  }
}
