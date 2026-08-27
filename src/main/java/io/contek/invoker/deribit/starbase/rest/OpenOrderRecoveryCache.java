package io.contek.invoker.deribit.starbase.rest;

import io.contek.invoker.deribit.starbase.common.NanoClock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Synchronous single-flight cache respecting the documented per-portfolio REST limit. */
public final class OpenOrderRecoveryCache {

  public static final Duration MINIMUM_REFRESH_INTERVAL = Duration.ofMinutes(1);

  private final NanoClock clock;
  private final long intervalNanos;
  private final OpenOrderSnapshotLoader loader;
  private List<StarbaseOpenOrder> snapshot;
  private RuntimeException lastFailure;
  private boolean attempted;
  private long validUntilNanos = Long.MIN_VALUE;
  private long nextRefreshNanos = Long.MIN_VALUE;
  private long failureCount;
  private long successfulRefreshCount;
  private long lastSuccessfulRefreshNanos;

  public OpenOrderRecoveryCache(
      NanoClock clock, Duration refreshInterval, OpenOrderSnapshotLoader loader) {
    this.clock = Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(refreshInterval, "refreshInterval");
    if (refreshInterval.compareTo(MINIMUM_REFRESH_INTERVAL) < 0) {
      throw new IllegalArgumentException("refreshInterval must be at least one minute");
    }
    try {
      intervalNanos = refreshInterval.toNanos();
    } catch (ArithmeticException overflow) {
      throw new IllegalArgumentException("refreshInterval is too large", overflow);
    }
    this.loader = Objects.requireNonNull(loader, "loader");
  }

  public synchronized List<StarbaseOpenOrder> get() {
    long now = clock.nanoTime();
    if (snapshot != null && before(now, validUntilNanos)) return snapshot;
    if (attempted && before(now, nextRefreshNanos)) return requireSnapshot();
    return load(now);
  }

  /** Requests a fresh value but never bypasses the configured server rate limit. */
  public synchronized List<StarbaseOpenOrder> refresh() {
    long now = clock.nanoTime();
    if (attempted && before(now, nextRefreshNanos)) return requireSnapshot();
    return load(now);
  }

  /** Expires the cache locally; the next request refreshes once the rate window permits. */
  public synchronized void invalidate() {
    validUntilNanos = Long.MIN_VALUE;
  }

  public synchronized List<StarbaseOpenOrder> current() {
    return requireSnapshot();
  }

  public synchronized boolean hasSnapshot() {
    return snapshot != null;
  }

  public synchronized long nextRefreshNanos() {
    return nextRefreshNanos;
  }

  public synchronized long failureCount() {
    return failureCount;
  }

  public synchronized long successfulRefreshCount() {
    return successfulRefreshCount;
  }

  public synchronized long lastSuccessfulRefreshNanos() {
    if (snapshot == null) {
      throw new IllegalStateException("no successful open-order snapshot is available");
    }
    return lastSuccessfulRefreshNanos;
  }

  public synchronized boolean isSnapshotFresh() {
    return snapshot != null && before(clock.nanoTime(), validUntilNanos);
  }

  private List<StarbaseOpenOrder> load(long now) {
    attempted = true;
    nextRefreshNanos = saturatedAdd(now, intervalNanos);
    try {
      List<StarbaseOpenOrder> loaded = List.copyOf(Objects.requireNonNull(loader.load(), "snapshot"));
      snapshot = loaded;
      lastFailure = null;
      lastSuccessfulRefreshNanos = now;
      successfulRefreshCount =
          successfulRefreshCount == Long.MAX_VALUE
              ? Long.MAX_VALUE
              : successfulRefreshCount + 1;
      validUntilNanos = nextRefreshNanos;
      return loaded;
    } catch (RuntimeException failure) {
      lastFailure = failure;
      failureCount = failureCount == Long.MAX_VALUE ? Long.MAX_VALUE : failureCount + 1;
      throw failure;
    }
  }

  private List<StarbaseOpenOrder> requireSnapshot() {
    if (snapshot != null) return snapshot;
    throw new IllegalStateException("no open-order recovery snapshot is available", lastFailure);
  }

  private static boolean before(long now, long deadline) {
    return now - deadline < 0;
  }

  private static long saturatedAdd(long value, long increment) {
    return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
  }
}
