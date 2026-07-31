package io.contek.invoker.deribit.starbase.codec.common;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WirePrimitivesTest {

  private static volatile long sink;

  public void testAlign8HandlesBoundariesAndRejectsInvalidOrOverflowingLengths() {
    assertEquals(0, WirePrimitives.align8(0));
    assertEquals(8, WirePrimitives.align8(1));
    assertEquals(8, WirePrimitives.align8(7));
    assertEquals(8, WirePrimitives.align8(8));
    assertEquals(16, WirePrimitives.align8(9));
    assertEquals(2_147_483_640, WirePrimitives.align8(2_147_483_640));
    assertThrows(IllegalArgumentException.class, () -> WirePrimitives.align8(-1));
    assertThrows(IllegalArgumentException.class, () -> WirePrimitives.align8(Integer.MAX_VALUE));
  }

  public void testBoundsUseAbsoluteLimitAndRejectNegativeOrOverflowingRanges() {
    ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
    buffer.position(12);

    WirePrimitives.requireBounds(buffer, 4, 12);

    assertEquals(12, buffer.position());
    assertThrows(
        StarbaseProtocolException.class, () -> WirePrimitives.requireBounds(buffer, -1, 1));
    assertThrows(
        StarbaseProtocolException.class, () -> WirePrimitives.requireBounds(buffer, 15, 2));
    assertThrows(
        StarbaseProtocolException.class,
        () -> WirePrimitives.requireBounds(buffer, Integer.MAX_VALUE, 8));
  }

  public void testUnsignedAccessIsLittleEndianAbsoluteAndRangeChecked() {
    ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
    WirePrimitives.putUInt8(buffer, 2, 255);
    WirePrimitives.putUInt16(buffer, 3, 65_534);

    assertEquals(255, WirePrimitives.getUInt8(buffer, 2));
    assertEquals(65_534, WirePrimitives.getUInt16(buffer, 3));
    assertEquals(0, buffer.position());
    assertThrows(IllegalArgumentException.class, () -> WirePrimitives.putUInt8(buffer, 0, 256));
    assertThrows(IllegalArgumentException.class, () -> WirePrimitives.putUInt16(buffer, 0, -1));
    assertThrows(
        StarbaseProtocolException.class,
        () -> WirePrimitives.getUInt8(buffer.order(ByteOrder.BIG_ENDIAN), 0));
  }

  public void testValidHotPathPrimitivesAllocateNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ByteBuffer buffer = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(4, (short) 0xFFFF);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(buffer, iteration);
    }

    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(buffer, iteration);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated, "valid wire primitive hot path allocated bytes");
  }

  private static void exercise(ByteBuffer buffer, int value) {
    WirePrimitives.requireBounds(buffer, 4, 2);
    sink += WirePrimitives.getUInt16(buffer, 4) + WirePrimitives.align8(value & 1023);
  }
}
