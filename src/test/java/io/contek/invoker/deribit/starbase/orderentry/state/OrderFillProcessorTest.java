package io.contek.invoker.deribit.starbase.orderentry.state;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

public final class OrderFillProcessorTest {

  private static final FillListener NOOP = (session, match, order, fill, remaining) -> {};
  private static volatile long sink;

  public void testImmediateAndLaterFillsShareOneExactMatchIdDeduplicationDomain() {
    LocalOrderStateStore orders = new LocalOrderStateStore(2);
    assertTrue(orders.registerPending(101, 501, 1, 20, 1_000));
    assertTrue(orders.place(11, 1, 101, 901, 1_000));
    CountingFillListener listener = new CountingFillListener();
    OrderFillProcessor processor = new OrderFillProcessor(orders, 8, listener);

    assertTrue(processor.onImmediateFill(11, 7001, 901, 400));
    assertEquals(600, orders.remainingQuantity(901));
    assertFalse(processor.onUnsolicitedFill(22, 7001, 901, 400));
    assertEquals(600, orders.remainingQuantity(901));
    assertTrue(processor.onUnsolicitedFill(22, 7002, 901, 600));
    assertEquals(LocalOrderStateStore.STATE_FILLED, orders.stateByOrderId(901));
    assertEquals(2, listener.count);
    assertEquals(7002, listener.lastMatchId);
    assertEquals(0, listener.lastRemainingQuantity);
  }

  public void testMultipleImmediateFillsAndSignedMatchIdsApplyExactlyOnce() {
    LocalOrderStateStore orders = placedOrder(12, 902, 10);
    CountingFillListener listener = new CountingFillListener();
    OrderFillProcessor processor = new OrderFillProcessor(orders, 4, listener);

    assertTrue(processor.onImmediateFill(1, Long.MIN_VALUE, 902, 2));
    assertTrue(processor.onImmediateFill(1, Long.MAX_VALUE, 902, 3));
    assertTrue(processor.onImmediateFill(1, -1, 902, 5));
    assertEquals(LocalOrderStateStore.STATE_FILLED, orders.stateByOrderId(902));
    assertEquals(3, processor.size());
    assertEquals(3, listener.count);
    assertFalse(processor.onUnsolicitedFill(2, Long.MIN_VALUE, 902, 2));
  }

  public void testInvalidUnknownAndTerminalFillsNeverEnterTheDeduplicationTable() {
    LocalOrderStateStore orders = placedOrder(13, 903, 5);
    OrderFillProcessor processor = new OrderFillProcessor(orders, 4, new CountingFillListener());

    assertThrows(IllegalArgumentException.class, () -> processor.onImmediateFill(1, 1, 903, 0));
    assertThrows(IllegalArgumentException.class, () -> processor.onImmediateFill(1, 1, 903, 6));
    assertFalse(processor.onImmediateFill(1, 1, 999, 1));
    assertEquals(0, processor.size());
    assertTrue(processor.onImmediateFill(1, 1, 903, 5));
    assertFalse(processor.onUnsolicitedFill(2, 2, 903, 1));
    assertEquals(1, processor.size());
  }

  public void testBoundedCapacityFailsBeforeMutatingTheOrder() {
    LocalOrderStateStore orders = placedOrder(14, 904, 3);
    OrderFillProcessor processor = new OrderFillProcessor(orders, 2, new CountingFillListener());
    assertTrue(processor.onImmediateFill(1, 1, 904, 1));
    assertTrue(processor.onImmediateFill(1, 2, 904, 1));
    assertThrows(IllegalStateException.class, () -> processor.onImmediateFill(1, 3, 904, 1));
    assertEquals(1, orders.remainingQuantity(904));
    assertEquals(2, processor.size());
  }

  public void testCallbackFailureStillMarksTheAppliedFillAsSeen() {
    LocalOrderStateStore orders = placedOrder(15, 905, 2);
    OrderFillProcessor processor =
        new OrderFillProcessor(
            orders,
            2,
            (sessionId, matchId, orderId, fillQuantity, remainingQuantity) -> {
              throw new IllegalStateException("callback failed");
            });
    assertThrows(IllegalStateException.class, () -> processor.onImmediateFill(1, 10, 905, 1));
    assertEquals(1, orders.remainingQuantity(905));
    assertEquals(1, processor.size());
    assertFalse(processor.onUnsolicitedFill(2, 10, 905, 1));
    assertEquals(1, orders.remainingQuantity(905));
  }

  public void testInvalidConstructionFailsExplicitly() {
    LocalOrderStateStore orders = new LocalOrderStateStore(1);
    assertThrows(IllegalArgumentException.class,
        () -> new OrderFillProcessor(orders, 0, new CountingFillListener()));
    assertThrows(NullPointerException.class,
        () -> new OrderFillProcessor(null, 1, new CountingFillListener()));
    assertThrows(NullPointerException.class, () -> new OrderFillProcessor(orders, 1, null));
  }

  public void testAuthoritativeReconciliationBoundaryReclaimsDeduplicationCapacity() {
    LocalOrderStateStore orders = placedOrder(16, 906, 1);
    OrderFillProcessor processor = new OrderFillProcessor(orders, 1, NOOP);
    assertTrue(processor.onImmediateFill(1, 77, 906, 1));
    assertThrows(IllegalStateException.class, () -> processor.onImmediateFill(1, 78, 906, 1));
    processor.resetAfterReconciliation();
    assertEquals(0, processor.size());
  }

  public void testWarmedFillLifecycleAllocatesNothing() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    LocalOrderStateStore orders = new LocalOrderStateStore(1);
    OrderFillProcessor processor = new OrderFillProcessor(orders, 1, NOOP);
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      exercise(orders, processor, iteration + 1L);
    }
    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(orders, processor, 1_000_001L + iteration);
    }
    assertEquals(
        0L,
        bean.getThreadAllocatedBytes(threadId) - before,
        "fill processing lifecycle allocated bytes");
  }

  private static void exercise(
      LocalOrderStateStore orders, OrderFillProcessor processor, long id) {
    if (!orders.registerPending(id, 1, 1, 2, 3)
        || !orders.place(1, 1, id, -id, 3)
        || !processor.onImmediateFill(1, id, -id, 3)
        || !orders.releaseTerminalByClientOrderId(id)) {
      throw new AssertionError("fill lifecycle failed");
    }
    processor.resetAfterReconciliation();
    sink = id;
  }

  private static LocalOrderStateStore placedOrder(
      long clientOrderId, long orderId, long quantity) {
    LocalOrderStateStore orders = new LocalOrderStateStore(2);
    assertTrue(orders.registerPending(clientOrderId, 501, 1, 20, quantity));
    assertTrue(orders.place(1, 1, clientOrderId, orderId, quantity));
    return orders;
  }

  private static final class CountingFillListener implements FillListener {
    private int count;
    private long lastMatchId;
    private long lastRemainingQuantity;

    @Override
    public void onFill(
        long sessionId,
        long matchId,
        long orderId,
        long fillQuantity,
        long remainingQuantity) {
      count++;
      lastMatchId = matchId;
      lastRemainingQuantity = remainingQuantity;
    }
  }
}
