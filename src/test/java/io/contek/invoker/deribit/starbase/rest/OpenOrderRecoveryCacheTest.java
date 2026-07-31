package io.contek.invoker.deribit.starbase.rest;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertSame;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class OpenOrderRecoveryCacheTest {

  public void testLoadsOnceCachesUntilExactMinuteBoundaryAndPublishesImmutableCopy() {
    FakeClock clock = new FakeClock(1_000L);
    AtomicInteger calls = new AtomicInteger();
    ArrayList<StarbaseOpenOrder> mutable = new ArrayList<>(List.of(order("one")));
    OpenOrderRecoveryCache cache = new OpenOrderRecoveryCache(
        clock, Duration.ofMinutes(1), () -> { calls.incrementAndGet(); return mutable; });

    List<StarbaseOpenOrder> first = cache.get();
    mutable.clear();
    assertSame(first, cache.get());
    assertEquals(1, first.size());
    assertThrows(UnsupportedOperationException.class, () -> first.add(order("bad")));
    assertEquals(1, calls.get());

    clock.set(60_000_000_999L);
    assertSame(first, cache.get());
    clock.set(60_000_001_000L);
    List<StarbaseOpenOrder> second = cache.get();
    assertEquals(2, calls.get());
    assertTrue(second.isEmpty());
  }

  public void testRefreshFailureRetainsLastGoodAndRateLimitsFurtherAttempts() {
    FakeClock clock = new FakeClock(0L);
    AtomicInteger calls = new AtomicInteger();
    OpenOrderRecoveryCache cache = new OpenOrderRecoveryCache(clock, Duration.ofMinutes(1), () -> {
      if (calls.incrementAndGet() == 1) return List.of(order("good"));
      throw new StarbaseRestException("gateway failed", 500, -1, null, false, null);
    });
    List<StarbaseOpenOrder> good = cache.get();

    cache.invalidate();
    assertSame(good, cache.get(), "invalidation must not bypass the server rate limit");
    clock.set(Duration.ofMinutes(1).toNanos());
    assertThrows(StarbaseRestException.class, cache::refresh);
    assertSame(good, cache.current());
    assertSame(good, cache.get(), "failed attempt is rate-limited and last-good is retained");
    assertEquals(2, calls.get());
    assertEquals(1L, cache.failureCount());
  }

  public void testConcurrentExpiredReadersShareOneRefresh() throws Exception {
    FakeClock clock = new FakeClock(0L);
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    OpenOrderRecoveryCache cache = new OpenOrderRecoveryCache(clock, Duration.ofMinutes(1), () -> {
      calls.incrementAndGet();
      entered.countDown();
      try {
        release.await();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(interrupted);
      }
      return List.of(order("shared"));
    });

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<List<StarbaseOpenOrder>> first = executor.submit(cache::get);
      entered.await();
      Future<List<StarbaseOpenOrder>> second = executor.submit(cache::get);
      release.countDown();
      assertSame(first.get(), second.get());
    }
    assertEquals(1, calls.get());
  }

  public void testInitialFailureHasNoFalseSnapshotAndConfigurationIsBounded() {
    FakeClock clock = new FakeClock(Long.MAX_VALUE - 10L);
    AtomicInteger calls = new AtomicInteger();
    OpenOrderRecoveryCache cache = new OpenOrderRecoveryCache(clock, Duration.ofMinutes(1), () -> {
      calls.incrementAndGet();
      throw new IllegalStateException("offline");
    });

    assertThrows(IllegalStateException.class, cache::get);
    assertFalse(cache.hasSnapshot());
    assertThrows(IllegalStateException.class, cache::current);
    assertThrows(IllegalStateException.class, cache::get);
    assertEquals(1, calls.get(), "overflow-safe deadline must still rate-limit an initial failure");
    assertEquals(Long.MAX_VALUE, cache.nextRefreshNanos());
    assertThrows(IllegalArgumentException.class,
        () -> new OpenOrderRecoveryCache(clock, Duration.ofSeconds(59), List::of));
  }

  private static StarbaseOpenOrder order(String id) {
    return new StarbaseOpenOrder(id, "BTC-PERPETUAL", StarbaseOrderSide.BUY,
        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
        StarbaseRestOrderState.OPEN, StarbaseRestOrderType.LIMIT, StarbaseTimeInForce.GTC,
        false, false, 1L, 1L, null, true, null, null, BigDecimal.ZERO);
  }

  private static final class FakeClock implements io.contek.invoker.deribit.starbase.common.NanoClock {
    private final AtomicLong now;
    private FakeClock(long initial) { now = new AtomicLong(initial); }
    @Override public long nanoTime() { return now.get(); }
    private void set(long value) { now.set(value); }
  }
}
