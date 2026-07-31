package io.contek.invoker.deribit.starbase.codec.common;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class DecimalCodecsTest {

  private static volatile long sink;

  public void testDecimal72UsesThePinnedNineByteLittleEndianLayoutWithoutMovingPosition() {
    ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
    buffer.position(15);

    Decimal72Codec.put(buffer, 2, 0x0102_0304_0506_0708L, -12);

    assertEquals(0x08, Byte.toUnsignedInt(buffer.get(2)));
    assertEquals(0x01, Byte.toUnsignedInt(buffer.get(9)));
    assertEquals(-12, buffer.get(10));
    assertEquals(0x0102_0304_0506_0708L, Decimal72Codec.mantissa(buffer, 2));
    assertEquals(-12, Decimal72Codec.exponent(buffer, 2));
    assertEquals(15, buffer.position());
  }

  public void testDecimal72SupportsOnlyTheCanonicalSbeNullEncoding() {
    ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);

    Decimal72Codec.putNull(buffer, 0);
    assertTrue(Decimal72Codec.isNull(buffer, 0));
    assertEquals(Long.MIN_VALUE, Decimal72Codec.mantissa(buffer, 0));
    assertEquals(Byte.MIN_VALUE, Decimal72Codec.exponent(buffer, 0));

    Decimal72Codec.put(buffer, 0, Long.MIN_VALUE + 1, Byte.MIN_VALUE + 1);
    assertFalse(Decimal72Codec.isNull(buffer, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> Decimal72Codec.put(buffer, 0, Long.MIN_VALUE, Byte.MIN_VALUE + 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> Decimal72Codec.put(buffer, 0, Long.MIN_VALUE + 1, Byte.MIN_VALUE));
  }

  public void testExactRescalingRejectsPrecisionLossOverflowNullAndInvalidExponent() {
    assertEquals(12_300L, Decimal72Codec.rescaleExact(123, -2, -4));
    assertEquals(123L, Decimal72Codec.rescaleExact(12_300, -4, -2));
    assertEquals(-123L, Decimal72Codec.rescaleExact(-12_300, -4, -2));
    assertThrows(ArithmeticException.class, () -> Decimal72Codec.rescaleExact(123, -4, -2));
    assertThrows(
        ArithmeticException.class,
        () -> Decimal72Codec.rescaleExact(Long.MAX_VALUE, -1, -2));
    assertThrows(
        ArithmeticException.class,
        () -> Decimal72Codec.rescaleExact(1, Byte.MAX_VALUE, Byte.MIN_VALUE + 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> Decimal72Codec.rescaleExact(Long.MIN_VALUE, 0, 0));
    assertThrows(
        IllegalArgumentException.class, () -> Decimal72Codec.rescaleExact(1, -129, 0));
  }

  public void testPrice9UsesAnEightByteMantissaAndConvertsOnlyExactly() {
    ByteBuffer buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
    buffer.position(11);

    Price9Codec.put(buffer, 2, 123_456_789L);
    assertEquals(123_456_789L, Price9Codec.mantissa(buffer, 2));
    assertEquals(-9, Price9Codec.EXPONENT);
    assertEquals(11, buffer.position());
    assertEquals(1_230L, Price9Codec.fromDecimal72Exact(123, -8));
    assertEquals(123L, Price9Codec.fromDecimal72Exact(1_230, -10));
    assertThrows(
        ArithmeticException.class, () -> Price9Codec.fromDecimal72Exact(1_231, -10));

    Price9Codec.putNull(buffer, 2);
    assertTrue(Price9Codec.isNull(buffer, 2));
    assertThrows(
        IllegalArgumentException.class, () -> Price9Codec.put(buffer, 2, Long.MIN_VALUE));
    assertThrows(
        IllegalArgumentException.class,
        () -> Price9Codec.fromDecimal72Exact(Long.MIN_VALUE, -9));
  }

  public void testCodecsRejectWrongEndianAndShortBuffers() {
    ByteBuffer bigEndian = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN);
    ByteBuffer shortBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);

    assertThrows(StarbaseProtocolException.class, () -> Decimal72Codec.mantissa(bigEndian, 0));
    assertThrows(StarbaseProtocolException.class, () -> Decimal72Codec.put(shortBuffer, 0, 1, 0));
    assertThrows(StarbaseProtocolException.class, () -> Price9Codec.mantissa(shortBuffer, 1));
  }

  public void testValidDecimalHotPathAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ByteBuffer buffer = ByteBuffer.allocateDirect(24).order(ByteOrder.LITTLE_ENDIAN);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(buffer, iteration);
    }

    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(buffer, iteration);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated, "valid decimal codec hot path allocated bytes");
  }

  private static void exercise(ByteBuffer buffer, int value) {
    Decimal72Codec.put(buffer, 0, value, -8);
    Price9Codec.put(buffer, 12, Price9Codec.fromDecimal72Exact(value, -8));
    sink += Decimal72Codec.mantissa(buffer, 0) + Price9Codec.mantissa(buffer, 12);
  }
}
