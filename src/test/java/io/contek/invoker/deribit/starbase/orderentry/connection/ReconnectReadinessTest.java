package io.contek.invoker.deribit.starbase.orderentry.connection;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import java.time.Duration;

public final class ReconnectReadinessTest {

  public void testDisconnectIsImmediatelyUnavailableAndReconnectRequiresFreshReconciliation() {
    MutableClock clock = new MutableClock();
    RecordingListener listener = new RecordingListener();
    ReconnectReadiness readiness =
        new ReconnectReadiness(
            clock, Duration.ofNanos(10), Duration.ofNanos(40), listener);

    readiness.start();
    assertEquals(ReconnectReadiness.ACTION_CONNECT, readiness.poll());
    readiness.onConnected();
    readiness.onAuthenticated();
    readiness.setSequenceValid(true);
    readiness.setReferenceReady(true);
    assertFalse(readiness.isReady());
    readiness.onReconciled();
    assertTrue(readiness.isReady());

    readiness.onDisconnected();
    assertFalse(readiness.isReady());
    assertTrue(readiness.sessionOrdersMayHaveBeenCanceled());
    clock.now = 9;
    assertEquals(ReconnectReadiness.ACTION_NONE, readiness.poll());
    clock.now = 10;
    assertEquals(ReconnectReadiness.ACTION_CONNECT, readiness.poll());
    readiness.onConnected();
    readiness.onAuthenticated();
    readiness.setSequenceValid(true);
    readiness.setReferenceReady(true);
    assertFalse(readiness.isReady());
    readiness.onReconciled();
    assertTrue(readiness.isReady());
    assertEquals(ReconnectReadiness.STATE_READY, listener.lastState);
  }

  public void testRepeatedConnectionFailuresUseExponentiallyBoundedBackoff() {
    MutableClock clock = new MutableClock();
    RecordingListener listener = new RecordingListener();
    ReconnectReadiness readiness = readiness(clock, listener);
    readiness.start();
    assertEquals(ReconnectReadiness.ACTION_CONNECT, readiness.poll());
    readiness.onConnectFailed();
    assertEquals(10, readiness.reconnectAtNanos());
    assertEquals(20, readiness.nextBackoffNanos());

    clock.now = 10;
    assertEquals(ReconnectReadiness.ACTION_CONNECT, readiness.poll());
    readiness.onConnectFailed();
    assertEquals(30, readiness.reconnectAtNanos());
    assertEquals(40, readiness.nextBackoffNanos());

    clock.now = 30;
    assertEquals(ReconnectReadiness.ACTION_CONNECT, readiness.poll());
    readiness.onConnectFailed();
    assertEquals(70, readiness.reconnectAtNanos());
    assertEquals(40, readiness.nextBackoffNanos());
    clock.now = 69;
    assertEquals(ReconnectReadiness.ACTION_NONE, readiness.poll());
    clock.now = 70;
    assertEquals(ReconnectReadiness.ACTION_CONNECT, readiness.poll());
  }

  public void testReadinessDropsIfAnyLiveGateBecomesInvalidAndSuccessResetsBackoff() {
    MutableClock clock = new MutableClock();
    ReconnectReadiness readiness = readiness(clock, new RecordingListener());
    makeReady(readiness);
    assertTrue(readiness.isReady());
    readiness.setSequenceValid(false);
    assertFalse(readiness.isReady());
    readiness.setSequenceValid(true);
    assertTrue(readiness.isReady());
    readiness.setReferenceReady(false);
    assertFalse(readiness.isReady());
    readiness.setReferenceReady(true);
    assertTrue(readiness.isReady());

    readiness.onDisconnected();
    assertEquals(10, readiness.reconnectAtNanos());
    assertEquals(20, readiness.nextBackoffNanos());
    clock.now = 10;
    readiness.poll();
    readiness.onConnected();
    readiness.onAuthenticated();
    readiness.setSequenceValid(true);
    readiness.setReferenceReady(true);
    readiness.onReconciled();
    assertEquals(10, readiness.nextBackoffNanos());
    assertFalse(readiness.sessionOrdersMayHaveBeenCanceled());
  }

  public void testInvalidTransitionsAndCloseFailClosedWithoutFurtherReconnects() {
    MutableClock clock = new MutableClock();
    RecordingListener listener = new RecordingListener();
    ReconnectReadiness readiness = readiness(clock, listener);
    assertThrows(IllegalStateException.class, readiness::onConnected);
    assertThrows(IllegalStateException.class, readiness::onAuthenticated);
    readiness.start();
    assertThrows(IllegalStateException.class, readiness::start);
    assertThrows(IllegalStateException.class, readiness::onDisconnected);
    readiness.close();
    readiness.close();
    clock.now = Long.MAX_VALUE;
    assertEquals(ReconnectReadiness.ACTION_NONE, readiness.poll());
    assertFalse(readiness.isConnected());
    assertFalse(readiness.isReady());
    assertEquals(ReconnectReadiness.STATE_CLOSED, listener.lastState);
  }

  private static ReconnectReadiness readiness(
      MutableClock clock, RecordingListener listener) {
    return new ReconnectReadiness(
        clock, Duration.ofNanos(10), Duration.ofNanos(40), listener);
  }

  private static void makeReady(ReconnectReadiness readiness) {
    readiness.start();
    readiness.poll();
    readiness.onConnected();
    readiness.onAuthenticated();
    readiness.setSequenceValid(true);
    readiness.setReferenceReady(true);
    readiness.onReconciled();
  }

  private static final class MutableClock
      implements io.contek.invoker.deribit.starbase.common.NanoClock {
    private long now;

    @Override
    public long nanoTime() {
      return now;
    }
  }

  private static final class RecordingListener implements SessionStateListener {
    private int lastState;

    @Override
    public void onStateChanged(int state) {
      lastState = state;
    }
  }
}
