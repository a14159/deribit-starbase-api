package io.contek.invoker.deribit.starbase.orderentry.state;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.common.NanoClock;
import io.contek.invoker.deribit.starbase.orderentry.connection.ReconnectReadiness;
import io.contek.invoker.deribit.starbase.rest.OpenOrderRecoveryCache;
import io.contek.invoker.deribit.starbase.rest.StarbaseOpenOrder;
import io.contek.invoker.deribit.starbase.rest.StarbaseOrderSide;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestOrderState;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestOrderType;
import io.contek.invoker.deribit.starbase.rest.StarbaseTimeInForce;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class OrderStateReconciliationTest {

  public void testExactLiveMatchesAndAbsentTerminalOrdersOpenReadiness() {
    MutableClock clock = new MutableClock();
    LocalOrderStateStore store = new LocalOrderStateStore(4);
    place(store, 1, 101);
    place(store, 2, -202);
    place(store, 3, 303);
    assertTrue(store.cancel(1, 2, 303));
    ReconnectReadiness readiness = preparedReadiness(clock);
    OpenOrderRecoveryCache cache = cache(clock, () -> List.of(order(101), order(-202)));
    OrderStateReconciliation reconciliation =
        reconciliation(clock, store, cache, readiness, Duration.ofMinutes(2));

    assertTrue(reconciliation.reconcile());
    assertEquals(OrderStateReconciliation.RESULT_MATCHED, reconciliation.lastResult());
    assertTrue(readiness.isReady());
  }

  public void testRestOnlySbeOnlyPendingAndTerminalMismatchesFailClosedWithoutTupleFallback() {
    MutableClock clock = new MutableClock();

    LocalOrderStateStore restOnly = new LocalOrderStateStore(2);
    place(restOnly, 1, 101);
    assertMismatch(
        clock,
        restOnly,
        List.of(order(101), order(999)),
        OrderStateReconciliation.RESULT_REST_ONLY);

    LocalOrderStateStore sbeOnly = new LocalOrderStateStore(2);
    place(sbeOnly, 1, 101);
    assertMismatch(clock, sbeOnly, List.of(), OrderStateReconciliation.RESULT_SBE_ONLY);

    LocalOrderStateStore pending = new LocalOrderStateStore(2);
    assertTrue(pending.registerPending(1, 7, 1, 10, 1));
    assertMismatch(clock, pending, List.of(), OrderStateReconciliation.RESULT_PENDING_LOCAL);

    LocalOrderStateStore terminal = new LocalOrderStateStore(2);
    place(terminal, 1, 101);
    assertTrue(terminal.cancel(1, 2, 101));
    assertMismatch(
        clock,
        terminal,
        List.of(order(101)),
        OrderStateReconciliation.RESULT_TERMINAL_MISMATCH);
  }

  public void testDuplicateSentinelAndAmbiguousPrimitiveIdentitiesFailClosed() {
    MutableClock clock = new MutableClock();
    LocalOrderStateStore store = new LocalOrderStateStore(2);
    place(store, 1, 101);

    assertMismatch(
        clock,
        store,
        List.of(order(101), order(101)),
        OrderStateReconciliation.RESULT_DUPLICATE_IDENTITY);
    assertMismatch(
        clock,
        store,
        List.of(order(Long.MIN_VALUE)),
        OrderStateReconciliation.RESULT_INVALID_IDENTITY);
  }

  public void testSnapshotFailureAndExcessAgeNeverRestoreReadiness() {
    MutableClock clock = new MutableClock();
    LocalOrderStateStore store = new LocalOrderStateStore(1);
    ReconnectReadiness failedReadiness = preparedReadiness(clock);
    OpenOrderRecoveryCache failed =
        cache(clock, () -> { throw new IllegalStateException("snapshot unavailable"); });
    OrderStateReconciliation failedReconciliation =
        reconciliation(clock, store, failed, failedReadiness, Duration.ofMinutes(2));

    assertFalse(failedReconciliation.reconcile());
    assertEquals(
        OrderStateReconciliation.RESULT_SNAPSHOT_FAILURE,
        failedReconciliation.lastResult());
    assertFalse(failedReadiness.isReady());

    OpenOrderRecoveryCache aged = cache(clock, List::of);
    assertTrue(aged.get().isEmpty());
    clock.now = 11;
    ReconnectReadiness agedReadiness = preparedReadiness(clock);
    OrderStateReconciliation agedReconciliation =
        reconciliation(clock, store, aged, agedReadiness, Duration.ofNanos(10));
    assertFalse(agedReconciliation.reconcile());
    assertEquals(OrderStateReconciliation.RESULT_SNAPSHOT_STALE, agedReconciliation.lastResult());
    assertFalse(agedReadiness.isReady());
  }

  public void testRefreshFailureDropsPreviouslyReadySessionDespiteRetainedLastGoodSnapshot() {
    MutableClock clock = new MutableClock();
    LocalOrderStateStore store = new LocalOrderStateStore(1);
    place(store, 1, 101);
    AtomicInteger loads = new AtomicInteger();
    OpenOrderRecoveryCache cache =
        cache(
            clock,
            () -> {
              if (loads.incrementAndGet() == 1) {
                return List.of(order(101));
              }
              throw new IllegalStateException("refresh failed");
            });
    ReconnectReadiness readiness = preparedReadiness(clock);
    OrderStateReconciliation reconciliation =
        reconciliation(clock, store, cache, readiness, Duration.ofMinutes(2));

    assertTrue(reconciliation.reconcile());
    assertTrue(readiness.isReady());
    clock.now = Duration.ofMinutes(1).toNanos();

    assertFalse(reconciliation.reconcile());
    assertEquals(OrderStateReconciliation.RESULT_SNAPSHOT_FAILURE, reconciliation.lastResult());
    assertTrue(reconciliation.lastFailure() instanceof IllegalStateException);
    assertEquals(1, cache.current().size());
    assertEquals(2, loads.get());
    assertFalse(readiness.isReady());
  }

  public void testDisconnectRequiresAPostDisconnectSnapshotBeforeReadinessCanReopen() {
    MutableClock clock = new MutableClock();
    LocalOrderStateStore store = new LocalOrderStateStore(1);
    place(store, 1, 101);
    AtomicInteger loads = new AtomicInteger();
    OpenOrderRecoveryCache cache =
        cache(clock, () -> { loads.incrementAndGet(); return List.of(order(101)); });
    ReconnectReadiness readiness = preparedReadiness(clock);
    OrderStateReconciliation reconciliation =
        reconciliation(clock, store, cache, readiness, Duration.ofMinutes(2));
    assertTrue(reconciliation.reconcile());
    assertTrue(readiness.isReady());

    reconciliation.onDisconnected();
    assertFalse(readiness.isReady());
    clock.now = 10;
    assertEquals(ReconnectReadiness.ACTION_CONNECT, readiness.poll());
    readiness.onConnected();
    readiness.onAuthenticated();
    readiness.setSequenceValid(true);
    readiness.setReferenceReady(true);
    assertFalse(reconciliation.reconcile());
    assertEquals(OrderStateReconciliation.RESULT_SNAPSHOT_STALE, reconciliation.lastResult());
    assertEquals(1, loads.get());

    clock.now = Duration.ofMinutes(1).toNanos();
    assertTrue(reconciliation.reconcile());
    assertEquals(2, loads.get());
    assertTrue(readiness.isReady());
  }

  private static void assertMismatch(
      MutableClock clock,
      LocalOrderStateStore store,
      List<StarbaseOpenOrder> snapshot,
      int expectedResult) {
    ReconnectReadiness readiness = preparedReadiness(clock);
    OrderStateReconciliation reconciliation =
        reconciliation(clock, store, cache(clock, () -> snapshot), readiness, Duration.ofMinutes(2));
    assertFalse(reconciliation.reconcile());
    assertEquals(expectedResult, reconciliation.lastResult());
    assertFalse(readiness.isReady());
  }

  private static OrderStateReconciliation reconciliation(
      NanoClock clock,
      LocalOrderStateStore store,
      OpenOrderRecoveryCache cache,
      ReconnectReadiness readiness,
      Duration maximumSnapshotAge) {
    return new OrderStateReconciliation(clock, maximumSnapshotAge, store, cache, readiness);
  }

  private static OpenOrderRecoveryCache cache(
      NanoClock clock,
      io.contek.invoker.deribit.starbase.rest.OpenOrderSnapshotLoader loader) {
    return new OpenOrderRecoveryCache(clock, Duration.ofMinutes(1), loader);
  }

  private static ReconnectReadiness preparedReadiness(MutableClock clock) {
    ReconnectReadiness readiness =
        new ReconnectReadiness(clock, Duration.ofNanos(10), Duration.ofNanos(40), state -> {});
    readiness.start();
    assertEquals(ReconnectReadiness.ACTION_CONNECT, readiness.poll());
    readiness.onConnected();
    readiness.onAuthenticated();
    readiness.setSequenceValid(true);
    readiness.setReferenceReady(true);
    return readiness;
  }

  private static void place(LocalOrderStateStore store, long clientOrderId, long orderId) {
    assertTrue(store.registerPending(clientOrderId, 7, 1, 10, 1));
    assertTrue(store.place(1, 1, clientOrderId, orderId, 1));
  }

  private static StarbaseOpenOrder order(long orderId) {
    return new StarbaseOpenOrder(
        orderId,
        "BTC-PERPETUAL",
        StarbaseOrderSide.BUY,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        StarbaseRestOrderState.OPEN,
        StarbaseRestOrderType.LIMIT,
        StarbaseTimeInForce.GTC,
        false,
        false,
        false,
        1L,
        1L,
        null,
        true,
        null,
        null,
        BigDecimal.ZERO);
  }

  private static final class MutableClock implements NanoClock {
    private long now;

    @Override
    public long nanoTime() {
      return now;
    }
  }
}
