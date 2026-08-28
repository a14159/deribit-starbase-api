package io.contek.invoker.deribit.starbase.orderentry.state;

import io.contek.invoker.deribit.starbase.common.NanoClock;
import io.contek.invoker.deribit.starbase.orderentry.connection.ReconnectReadiness;
import io.contek.invoker.deribit.starbase.rest.OpenOrderRecoveryCache;
import io.contek.invoker.deribit.starbase.rest.StarbaseOpenOrder;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Fail-closed exact-identity comparison between one REST snapshot and local SBE state. */
public final class OrderStateReconciliation {

  public static final int RESULT_NONE = 0;
  public static final int RESULT_MATCHED = 1;
  public static final int RESULT_SNAPSHOT_FAILURE = 2;
  public static final int RESULT_SNAPSHOT_STALE = 3;
  public static final int RESULT_INVALID_IDENTITY = 4;
  public static final int RESULT_DUPLICATE_IDENTITY = 5;
  public static final int RESULT_REST_ONLY = 6;
  public static final int RESULT_SBE_ONLY = 7;
  public static final int RESULT_TERMINAL_MISMATCH = 8;
  public static final int RESULT_PENDING_LOCAL = 9;
  public static final int RESULT_CAPACITY_EXHAUSTED = 10;
  public static final int RESULT_DISCONNECTED = 11;

  private final NanoClock clock;
  private final long maximumSnapshotAgeNanos;
  private final LocalOrderStateStore localOrders;
  private final OpenOrderRecoveryCache snapshots;
  private final ReconnectReadiness readiness;
  private final long[] snapshotOrderIds;
  private int lastResult;
  private RuntimeException lastFailure;
  private boolean requirePostDisconnectRefresh;
  private long disconnectSnapshotGeneration;

  public OrderStateReconciliation(
      NanoClock clock,
      Duration maximumSnapshotAge,
      LocalOrderStateStore localOrders,
      OpenOrderRecoveryCache snapshots,
      ReconnectReadiness readiness) {
    this.clock = Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(maximumSnapshotAge, "maximumSnapshotAge");
    if (maximumSnapshotAge.isZero() || maximumSnapshotAge.isNegative()) {
      throw new IllegalArgumentException("maximumSnapshotAge must be positive");
    }
    try {
      maximumSnapshotAgeNanos = maximumSnapshotAge.toNanos();
    } catch (ArithmeticException overflow) {
      throw new IllegalArgumentException("maximumSnapshotAge is too large", overflow);
    }
    this.localOrders = Objects.requireNonNull(localOrders, "localOrders");
    this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    this.readiness = Objects.requireNonNull(readiness, "readiness");
    snapshotOrderIds = new long[localOrders.capacity()];
  }

  public synchronized boolean reconcile() {
    readiness.onReconciliationFailed();
    lastFailure = null;
    final List<StarbaseOpenOrder> snapshot;
    try {
      snapshot = snapshots.refresh();
    } catch (RuntimeException failure) {
      lastFailure = failure;
      lastResult = RESULT_SNAPSHOT_FAILURE;
      return false;
    }
    if (!freshSnapshot()) {
      lastResult = RESULT_SNAPSHOT_STALE;
      return false;
    }
    if (snapshot.size() > snapshotOrderIds.length) {
      lastResult = RESULT_CAPACITY_EXHAUSTED;
      return false;
    }
    for (int index = 0; index < snapshot.size(); index++) {
      snapshotOrderIds[index] = snapshot.get(index).orderId();
    }
    int comparison = localOrders.compareOpenOrderIds(snapshotOrderIds, snapshot.size());
    lastResult = result(comparison);
    if (lastResult != RESULT_MATCHED) {
      return false;
    }
    requirePostDisconnectRefresh = false;
    readiness.onReconciled();
    return true;
  }

  public synchronized void onDisconnected() {
    readiness.onDisconnected();
    requireFreshSnapshotState();
    lastFailure = null;
    lastResult = RESULT_DISCONNECTED;
  }

  /** Closes an active peer gate until the shared cache advances past this recovery point. */
  public synchronized void requireFreshSnapshot() {
    int state = readiness.state();
    if (state == ReconnectReadiness.STATE_RECONCILING
        || state == ReconnectReadiness.STATE_READY) {
      readiness.onReconciliationFailed();
    }
    requireFreshSnapshotState();
    lastFailure = null;
    lastResult = RESULT_SNAPSHOT_STALE;
  }

  private void requireFreshSnapshotState() {
    disconnectSnapshotGeneration = snapshots.successfulRefreshCount();
    requirePostDisconnectRefresh = true;
    snapshots.invalidate();
  }

  public synchronized int lastResult() {
    return lastResult;
  }

  public synchronized RuntimeException lastFailure() {
    return lastFailure;
  }

  private boolean freshSnapshot() {
    if (!snapshots.isSnapshotFresh()) {
      return false;
    }
    if (requirePostDisconnectRefresh
        && snapshots.successfulRefreshCount() == disconnectSnapshotGeneration) {
      return false;
    }
    long age = clock.nanoTime() - snapshots.lastSuccessfulRefreshNanos();
    return age >= 0 && age <= maximumSnapshotAgeNanos;
  }

  private static int result(int comparison) {
    return switch (comparison) {
      case LocalOrderStateStore.RECONCILIATION_MATCHED -> RESULT_MATCHED;
      case LocalOrderStateStore.RECONCILIATION_INVALID_IDENTITY -> RESULT_INVALID_IDENTITY;
      case LocalOrderStateStore.RECONCILIATION_DUPLICATE_IDENTITY -> RESULT_DUPLICATE_IDENTITY;
      case LocalOrderStateStore.RECONCILIATION_REST_ONLY -> RESULT_REST_ONLY;
      case LocalOrderStateStore.RECONCILIATION_SBE_ONLY -> RESULT_SBE_ONLY;
      case LocalOrderStateStore.RECONCILIATION_TERMINAL_MISMATCH -> RESULT_TERMINAL_MISMATCH;
      case LocalOrderStateStore.RECONCILIATION_PENDING_LOCAL -> RESULT_PENDING_LOCAL;
      default -> throw new IllegalStateException("unknown local reconciliation result: " + comparison);
    };
  }
}
