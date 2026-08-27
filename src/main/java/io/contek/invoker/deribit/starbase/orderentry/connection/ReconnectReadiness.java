package io.contek.invoker.deribit.starbase.orderentry.connection;

import io.contek.invoker.deribit.starbase.common.NanoClock;
import io.contek.invoker.deribit.starbase.orderentry.command.OrderCommandReadiness;
import java.time.Duration;
import java.util.Objects;

/** Bounded reconnect scheduling and fail-closed trading-readiness gates. */
public final class ReconnectReadiness implements AutoCloseable, OrderCommandReadiness {

  public static final int ACTION_NONE = 0;
  public static final int ACTION_CONNECT = 1;

  public static final int STATE_NEW = 0;
  public static final int STATE_DISCONNECTED = 1;
  public static final int STATE_CONNECTING = 2;
  public static final int STATE_AUTHENTICATING = 3;
  public static final int STATE_RECONCILING = 4;
  public static final int STATE_READY = 5;
  public static final int STATE_CLOSED = 6;

  private final NanoClock clock;
  private final long initialBackoffNanos;
  private final long maximumBackoffNanos;
  private final SessionStateListener listener;
  private int state = STATE_NEW;
  private long reconnectAtNanos;
  private long nextBackoffNanos;
  private boolean authenticated;
  private boolean sequenceValid;
  private boolean referenceReady;
  private boolean reconciled;
  private boolean sessionOrdersMayHaveBeenCanceled;

  public ReconnectReadiness(
      NanoClock clock,
      Duration initialBackoff,
      Duration maximumBackoff,
      SessionStateListener listener) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.initialBackoffNanos = positiveNanos(initialBackoff, "initialBackoff");
    this.maximumBackoffNanos = positiveNanos(maximumBackoff, "maximumBackoff");
    if (maximumBackoffNanos < initialBackoffNanos) {
      throw new IllegalArgumentException("maximumBackoff must not be less than initialBackoff");
    }
    this.listener = Objects.requireNonNull(listener, "listener");
    this.nextBackoffNanos = initialBackoffNanos;
  }

  public synchronized void start() {
    requireState(STATE_NEW, "start");
    reconnectAtNanos = clock.nanoTime();
    transition(STATE_DISCONNECTED);
  }

  public synchronized int poll() {
    if (state != STATE_DISCONNECTED) {
      return ACTION_NONE;
    }
    if (!deadlineReached(clock.nanoTime(), reconnectAtNanos)) {
      return ACTION_NONE;
    }
    transition(STATE_CONNECTING);
    return ACTION_CONNECT;
  }

  public synchronized void onConnected() {
    requireState(STATE_CONNECTING, "connected");
    transition(STATE_AUTHENTICATING);
  }

  public synchronized void onAuthenticated() {
    requireState(STATE_AUTHENTICATING, "authenticated");
    authenticated = true;
    transition(STATE_RECONCILING);
    updateReadiness();
  }

  public synchronized void setSequenceValid(boolean valid) {
    requireActiveSession("sequence state");
    sequenceValid = valid;
    updateReadiness();
  }

  public synchronized void setReferenceReady(boolean ready) {
    requireActiveSession("reference state");
    referenceReady = ready;
    updateReadiness();
  }

  public synchronized void onReconciled() {
    requireActiveSession("reconciliation");
    reconciled = true;
    updateReadiness();
  }

  public synchronized void onReconciliationFailed() {
    requireActiveSession("reconciliation failure");
    reconciled = false;
    updateReadiness();
  }

  public synchronized void onDisconnected() {
    if (state == STATE_NEW || state == STATE_CLOSED || state == STATE_DISCONNECTED) {
      throw new IllegalStateException("disconnect is invalid in state " + state);
    }
    sessionOrdersMayHaveBeenCanceled = true;
    clearReadiness();
    scheduleReconnect();
  }

  public synchronized void onConnectFailed() {
    requireState(STATE_CONNECTING, "connect failure");
    clearReadiness();
    scheduleReconnect();
  }

  public synchronized boolean isReady() {
    return state == STATE_READY;
  }

  public synchronized boolean isConnected() {
    return state == STATE_AUTHENTICATING
        || state == STATE_RECONCILING
        || state == STATE_READY;
  }

  public synchronized int state() {
    return state;
  }

  public synchronized long reconnectAtNanos() {
    return reconnectAtNanos;
  }

  public synchronized long nextBackoffNanos() {
    return nextBackoffNanos;
  }

  public synchronized boolean sessionOrdersMayHaveBeenCanceled() {
    return sessionOrdersMayHaveBeenCanceled;
  }

  @Override
  public synchronized void close() {
    if (state != STATE_CLOSED) {
      clearReadiness();
      transition(STATE_CLOSED);
    }
  }

  private void updateReadiness() {
    boolean ready = authenticated && sequenceValid && referenceReady && reconciled;
    if (ready && state != STATE_READY) {
      sessionOrdersMayHaveBeenCanceled = false;
      nextBackoffNanos = initialBackoffNanos;
      transition(STATE_READY);
    } else if (!ready && state == STATE_READY) {
      transition(STATE_RECONCILING);
    }
  }

  private void scheduleReconnect() {
    long now = clock.nanoTime();
    reconnectAtNanos = saturatingAdd(now, nextBackoffNanos);
    nextBackoffNanos = Math.min(maximumBackoffNanos, saturatingDouble(nextBackoffNanos));
    transition(STATE_DISCONNECTED);
  }

  private void clearReadiness() {
    authenticated = false;
    sequenceValid = false;
    referenceReady = false;
    reconciled = false;
  }

  private void transition(int nextState) {
    state = nextState;
    listener.onStateChanged(nextState);
  }

  private void requireActiveSession(String operation) {
    if (state != STATE_RECONCILING && state != STATE_READY) {
      throw new IllegalStateException(operation + " is invalid in state " + state);
    }
  }

  private void requireState(int required, String operation) {
    if (state != required) {
      throw new IllegalStateException(operation + " is invalid in state " + state);
    }
  }

  private static boolean deadlineReached(long now, long deadline) {
    return now - deadline >= 0;
  }

  private static long saturatingAdd(long value, long increment) {
    if (value > Long.MAX_VALUE - increment) {
      return Long.MAX_VALUE;
    }
    return value + increment;
  }

  private static long saturatingDouble(long value) {
    return value > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : value * 2;
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
