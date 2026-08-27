package io.contek.invoker.deribit.starbase.orderentry.connection;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.orderentry.HeartbeatCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.TestRequestCodec;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;

public final class SessionLivenessTest {

  private static volatile int sink;

  public void testFakeClockSchedulesHeartbeatAndFailsAtExactPeerInactivityBoundary() {
    MutableClock clock = new MutableClock();
    CountingTransport transport = new CountingTransport();
    SessionLiveness liveness =
        new SessionLiveness(
            new TcpFrameWriter(64, transport),
            clock,
            Duration.ofNanos(10),
            Duration.ofNanos(30));
    liveness.start();

    clock.now = 9;
    assertEquals(SessionLiveness.ACTION_NONE, liveness.poll(1, 0));
    clock.now = 10;
    assertEquals(SessionLiveness.ACTION_HEARTBEAT, liveness.poll(1, 0));
    assertEquals(1, transport.writes);
    assertFalse(liveness.isFailed());

    clock.now = 29;
    assertEquals(SessionLiveness.ACTION_HEARTBEAT, liveness.poll(2, 0));
    clock.now = 30;
    assertEquals(SessionLiveness.ACTION_DISCONNECT, liveness.poll(3, 0));
    assertTrue(liveness.isFailed());
  }

  public void testDelayedPeerActivityMovesOnlyTheInactivityDeadline() {
    MutableClock clock = new MutableClock();
    CountingTransport transport = new CountingTransport();
    SessionLiveness liveness = liveness(clock, transport);
    liveness.start();
    clock.now = 25;
    liveness.onPeerActivity();
    clock.now = 30;
    assertEquals(SessionLiveness.ACTION_HEARTBEAT, liveness.poll(1, 0));
    clock.now = 54;
    assertEquals(SessionLiveness.ACTION_HEARTBEAT, liveness.poll(2, 0));
    assertFalse(liveness.isFailed());
    clock.now = 55;
    assertEquals(SessionLiveness.ACTION_DISCONNECT, liveness.poll(3, 0));
  }

  public void testTestRequestGetsExactCorrelatedHeartbeatAndCountsAsPeerActivity() {
    MutableClock clock = new MutableClock();
    CountingTransport transport = new CountingTransport();
    SessionLiveness liveness = liveness(clock, transport);
    liveness.start();
    ByteBuffer request = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
    TestRequestCodec.encode(request, 0, 99, 1, 0, 2);
    clock.now = 20;

    assertTrue(liveness.onTestRequest(request, 0, 7, 6));
    assertEquals(99, transport.lastCorrelationId);
    clock.now = 49;
    assertFalse(liveness.isFailed());
    liveness.poll(8, 7);
    clock.now = 50;
    assertEquals(SessionLiveness.ACTION_DISCONNECT, liveness.poll(9, 8));
  }

  public void testZeroProgressHeartbeatIsResumedWithoutASecondEncode() {
    MutableClock clock = new MutableClock();
    PartialTransport transport = new PartialTransport();
    SessionLiveness liveness = liveness(clock, transport);
    liveness.start();
    clock.now = 10;
    assertEquals(SessionLiveness.ACTION_NONE, liveness.poll(1, 0));
    assertEquals(1, transport.calls);
    transport.writable = true;
    clock.now = 11;
    assertEquals(SessionLiveness.ACTION_HEARTBEAT, liveness.poll(2, 0));
    assertEquals(2, transport.calls);
  }

  public void testSharedSequenceIsClaimedOnlyWhenHeartbeatCanActuallyStart() {
    MutableClock clock = new MutableClock();
    PartialTransport transport = new PartialTransport();
    TcpFrameWriter writer = new TcpFrameWriter(64, transport);
    SessionLiveness liveness =
        new SessionLiveness(
            writer, clock, Duration.ofNanos(10), Duration.ofNanos(30));
    SessionSequenceState sequences = new SessionSequenceState(1, 1);
    liveness.start();
    assertTrue(
        !writer.write(
            (buffer, offset) -> HeartbeatCodec.encode(buffer, offset, 7, 1, 0, 1)));

    clock.now = 10;
    assertEquals(SessionLiveness.ACTION_NONE, liveness.poll(sequences));
    assertEquals(1L, sequences.nextOutbound());
    transport.writable = true;
    assertTrue(writer.flush());
    assertEquals(SessionLiveness.ACTION_HEARTBEAT, liveness.poll(sequences));
    assertEquals(2L, sequences.nextOutbound());
  }

  public void testWarmedPeerActivityAndHeartbeatPollingAllocateNothing() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    MutableClock clock = new MutableClock();
    CountingTransport transport = new CountingTransport();
    SessionLiveness liveness = liveness(clock, transport);
    liveness.start();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      exercise(liveness, clock, iteration + 1L);
    }
    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(liveness, clock, 1_000_001L + iteration);
    }
    assertEquals(
        0L,
        bean.getThreadAllocatedBytes(threadId) - before,
        "session liveness hot path allocated bytes");
  }

  private static SessionLiveness liveness(
      MutableClock clock, TcpFrameTransport transport) {
    return new SessionLiveness(
        new TcpFrameWriter(64, transport),
        clock,
        Duration.ofNanos(10),
        Duration.ofNanos(30));
  }

  private static void exercise(
      SessionLiveness liveness, MutableClock clock, long sequence) {
    clock.now += 10;
    liveness.onPeerActivity();
    sink = liveness.poll(sequence, sequence - 1);
  }

  private static final class MutableClock
      implements io.contek.invoker.deribit.starbase.common.NanoClock {
    private long now;

    @Override
    public long nanoTime() {
      return now;
    }
  }

  private static final class CountingTransport implements TcpFrameTransport {
    private int writes;
    private long lastCorrelationId;

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      HeartbeatCodec.validate(buffer, offset);
      lastCorrelationId = HeartbeatCodec.correlationId(buffer, offset);
      writes++;
      return length;
    }
  }

  private static final class PartialTransport implements TcpFrameTransport {
    private int calls;
    private boolean writable;

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      calls++;
      return writable ? length : 0;
    }
  }
}
