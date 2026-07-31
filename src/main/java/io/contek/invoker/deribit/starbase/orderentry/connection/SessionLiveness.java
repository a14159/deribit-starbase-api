package io.contek.invoker.deribit.starbase.orderentry.connection;

import io.contek.invoker.deribit.starbase.codec.orderentry.HeartbeatCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.TestRequestCodec;
import io.contek.invoker.deribit.starbase.common.NanoClock;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;

/** Fake-clock-friendly heartbeat scheduler and peer inactivity gate. */
public final class SessionLiveness implements TcpFrameEncoder {

  public static final int ACTION_NONE = 0;
  public static final int ACTION_HEARTBEAT = 1;
  public static final int ACTION_DISCONNECT = 2;

  private final TcpFrameWriter writer;
  private final NanoClock clock;
  private final long heartbeatIntervalNanos;
  private final long inactivityTimeoutNanos;
  private boolean started;
  private boolean failed;
  private boolean pendingHeartbeat;
  private long lastPeerActivityNanos;
  private long lastSendNanos;
  private long correlationId;
  private long sequence;
  private long lastProcessedSequence;

  public SessionLiveness(
      TcpFrameWriter writer,
      NanoClock clock,
      Duration heartbeatInterval,
      Duration inactivityTimeout) {
    this.writer = Objects.requireNonNull(writer, "writer");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.heartbeatIntervalNanos = positiveNanos(heartbeatInterval, "heartbeatInterval");
    this.inactivityTimeoutNanos = positiveNanos(inactivityTimeout, "inactivityTimeout");
    if (inactivityTimeoutNanos <= heartbeatIntervalNanos) {
      throw new IllegalArgumentException("inactivityTimeout must exceed heartbeatInterval");
    }
  }

  public synchronized void start() {
    if (started) {
      throw new IllegalStateException("liveness already started");
    }
    started = true;
    long now = clock.nanoTime();
    lastPeerActivityNanos = now;
    lastSendNanos = now;
  }

  public synchronized int poll(long sequence, long lastProcessedSequence) {
    requireActive();
    long now = clock.nanoTime();
    if (elapsed(now, lastPeerActivityNanos) >= inactivityTimeoutNanos) {
      failed = true;
      pendingHeartbeat = false;
      return ACTION_DISCONNECT;
    }
    if (pendingHeartbeat) {
      if (writer.flush()) {
        pendingHeartbeat = false;
        lastSendNanos = now;
        return ACTION_HEARTBEAT;
      }
      return ACTION_NONE;
    }
    if (elapsed(now, lastSendNanos) < heartbeatIntervalNanos) {
      return ACTION_NONE;
    }
    prepareHeartbeat(0, sequence, lastProcessedSequence);
    boolean complete = writer.write(this);
    if (complete) {
      lastSendNanos = now;
      return ACTION_HEARTBEAT;
    }
    pendingHeartbeat = true;
    return ACTION_NONE;
  }

  /** Records any completely validated inbound session message. */
  public synchronized void onPeerActivity() {
    requireActive();
    lastPeerActivityNanos = clock.nanoTime();
  }

  /** Responds to a validated peer TestRequest with the exact correlation ID. */
  public synchronized boolean onTestRequest(
      ByteBuffer buffer, int offset, long sequence, long lastProcessedSequence) {
    requireActive();
    TestRequestCodec.validate(buffer, offset);
    onPeerActivity();
    if (pendingHeartbeat) {
      throw new StarbaseProtocolException("heartbeat already pending");
    }
    prepareHeartbeat(
        TestRequestCodec.correlationId(buffer, offset), sequence, lastProcessedSequence);
    boolean complete = writer.write(this);
    if (complete) {
      lastSendNanos = clock.nanoTime();
    } else {
      pendingHeartbeat = true;
    }
    return complete;
  }

  @Override
  public synchronized int encode(ByteBuffer buffer, int offset) {
    requireActive();
    return HeartbeatCodec.encode(
        buffer,
        offset,
        correlationId,
        sequence,
        lastProcessedSequence,
        clock.nanoTime());
  }

  public synchronized boolean isFailed() {
    return failed;
  }

  private void prepareHeartbeat(
      long correlationId, long sequence, long lastProcessedSequence) {
    if (correlationId < 0 || sequence < 1 || lastProcessedSequence < 0) {
      throw new IllegalArgumentException("invalid heartbeat sequence/correlation");
    }
    this.correlationId = correlationId;
    this.sequence = sequence;
    this.lastProcessedSequence = lastProcessedSequence;
  }

  private void requireActive() {
    if (!started || failed) {
      throw new IllegalStateException("session liveness is not active");
    }
  }

  private static long elapsed(long now, long then) {
    long difference = now - then;
    return difference < 0 ? 0 : difference;
  }

  private static long positiveNanos(Duration duration, String name) {
    Objects.requireNonNull(duration, name);
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    try {
      return duration.toNanos();
    } catch (ArithmeticException overflow) {
      throw new IllegalArgumentException(name + " is too large", overflow);
    }
  }
}
