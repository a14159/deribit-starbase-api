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

public final class SnapshotDecodersTest {

  private static volatile long sink;

  public void testSnapshotHeaderAndTrailerDecodeExactIncrementalAnchor() {
    ByteBuffer header = snapshotMessage(100, 11, 22, Long.MAX_VALUE, 1);
    SnapshotHeaderDecoder.validate(header, 0);
    assertEquals(11, SnapshotHeaderDecoder.instrumentId(header, 0));
    assertEquals(22, SnapshotHeaderDecoder.incrementalTimestampNanos(header, 0));
    assertEquals(Long.MAX_VALUE, SnapshotHeaderDecoder.incrementalSequenceNumber(header, 0));
    assertTrue(SnapshotHeaderDecoder.isStartOfTransaction(header, 0));
    assertFalse(SnapshotHeaderDecoder.isEndOfTransaction(header, 0));

    ByteBuffer trailer = snapshotMessage(101, 11, 22, Long.MAX_VALUE, 2);
    SnapshotTrailerDecoder.validate(trailer, 0);
    assertEquals(11, SnapshotTrailerDecoder.instrumentId(trailer, 0));
    assertEquals(22, SnapshotTrailerDecoder.incrementalTimestampNanos(trailer, 0));
    assertEquals(Long.MAX_VALUE, SnapshotTrailerDecoder.incrementalSequenceNumber(trailer, 0));
    assertFalse(SnapshotTrailerDecoder.isStartOfTransaction(trailer, 0));
    assertTrue(SnapshotTrailerDecoder.isEndOfTransaction(trailer, 0));
  }

  public void testEndOfCycleDecodesNonnegativeActiveInstrumentCount() {
    ByteBuffer end = message(16 + 4, 119, 0);
    end.putInt(16, Integer.MAX_VALUE);

    EndOfCycleDecoder.validate(end, 0);

    assertEquals(Integer.MAX_VALUE, EndOfCycleDecoder.activeInstrumentCount(end, 0));
  }

  public void testSnapshotDecodersRejectWrongTemplateLengthFlagsAndRequiredNulls() {
    assertThrows(
        StarbaseProtocolException.class,
        () -> SnapshotHeaderDecoder.validate(snapshotMessage(101, 1, 2, 3, 1), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> SnapshotHeaderDecoder.validate(message(16 + 23, 100, 1), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> SnapshotHeaderDecoder.validate(snapshotMessage(100, Long.MIN_VALUE, 2, 3, 1), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> SnapshotHeaderDecoder.validate(snapshotMessage(100, 1, 2, 3, 0), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> SnapshotTrailerDecoder.validate(snapshotMessage(101, 1, 2, 3, 3), 0));

    ByteBuffer negativeCount = message(16 + 4, 119, 0);
    negativeCount.putInt(16, -1);
    assertThrows(
        StarbaseProtocolException.class, () -> EndOfCycleDecoder.validate(negativeCount, 0));
  }

  public void testBoundaryTrackerRejectsInvalidOrderingAndMismatchedAnchors() {
    SnapshotBoundaryTracker tracker = new SnapshotBoundaryTracker();
    tracker.onHeader(1, 2, 3);
    assertTrue(tracker.isSnapshotOpen());
    assertThrows(StarbaseProtocolException.class, () -> tracker.onHeader(1, 2, 3));
    assertThrows(StarbaseProtocolException.class, tracker::onEndOfCycle);
    assertThrows(StarbaseProtocolException.class, () -> tracker.onTrailer(1, 2, 4));
    assertTrue(tracker.isSnapshotOpen());

    tracker.onTrailer(1, 2, 3);
    assertFalse(tracker.isSnapshotOpen());
    tracker.onEndOfCycle();
    assertThrows(StarbaseProtocolException.class, () -> tracker.onTrailer(1, 2, 3));
  }

  public void testValidSnapshotBoundaryHotPathAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ByteBuffer header = snapshotMessage(100, 1, 2, 3, 1);
    ByteBuffer trailer = snapshotMessage(101, 1, 2, 3, 2);
    SnapshotBoundaryTracker tracker = new SnapshotBoundaryTracker();
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(header, trailer, tracker);
    }

    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(header, trailer, tracker);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated, "valid snapshot boundary hot path allocated bytes");
  }

  private static ByteBuffer snapshotMessage(
      int templateId, long instrumentId, long timestamp, long sequence, int flags) {
    ByteBuffer buffer = message(16 + 24, templateId, flags);
    buffer.putLong(16, instrumentId);
    buffer.putLong(24, timestamp);
    buffer.putLong(32, sequence);
    return buffer;
  }

  private static ByteBuffer message(int length, int templateId, int flags) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(0, (short) length);
    buffer.putShort(2, (short) templateId);
    buffer.putShort(4, (short) 1);
    buffer.putShort(6, (short) flags);
    return buffer;
  }

  private static void exercise(
      ByteBuffer header, ByteBuffer trailer, SnapshotBoundaryTracker tracker) {
    SnapshotHeaderDecoder.validate(header, 0);
    SnapshotTrailerDecoder.validate(trailer, 0);
    tracker.onHeader(
        SnapshotHeaderDecoder.instrumentId(header, 0),
        SnapshotHeaderDecoder.incrementalTimestampNanos(header, 0),
        SnapshotHeaderDecoder.incrementalSequenceNumber(header, 0));
    tracker.onTrailer(
        SnapshotTrailerDecoder.instrumentId(trailer, 0),
        SnapshotTrailerDecoder.incrementalTimestampNanos(trailer, 0),
        SnapshotTrailerDecoder.incrementalSequenceNumber(trailer, 0));
    tracker.onEndOfCycle();
    sink += tracker.completedSnapshotCount();
  }
}
