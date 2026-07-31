package io.contek.invoker.deribit.starbase.orderentry.state;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

public final class LocalOrderStateStoreTest {

  private static volatile long sink;

  public void testConsolidatesAPlacedOrderAndCrossSessionLifecycleByExchangeId() {
    LocalOrderStateStore store = new LocalOrderStateStore(8);

    assertTrue(store.registerPending(101, 5001, 1, 20, 1_000));
    assertTrue(store.place(11, 1, 101, 9001, 1_000));
    assertTrue(store.amend(22, 1, 9001, 800));

    assertEquals(LocalOrderStateStore.STATE_OPEN, store.stateByOrderId(9001));
    assertEquals(101, store.clientOrderId(9001));
    assertEquals(5001, store.instrumentId(9001));
    assertEquals(11, store.originSessionId(9001));
    assertEquals(22, store.lastSessionId(9001));
    assertEquals(800, store.remainingQuantity(9001));
  }

  public void testPartialAndFullFillThenCancelFollowOnlyLegalTerminalTransitions() {
    LocalOrderStateStore store = placedStore(1_001, 9_001, 1_000);

    assertTrue(store.fill(11, 2, 9_001, 600));
    assertEquals(LocalOrderStateStore.STATE_PARTIALLY_FILLED, store.stateByOrderId(9_001));
    assertEquals(600, store.remainingQuantity(9_001));
    assertTrue(store.fill(22, 1, 9_001, 0));
    assertEquals(LocalOrderStateStore.STATE_FILLED, store.stateByOrderId(9_001));
    assertFalse(store.cancel(11, 3, 9_001));
    assertFalse(store.amend(11, 3, 9_001, 500));

    LocalOrderStateStore canceled = placedStore(1_002, 9_002, 1_000);
    assertTrue(canceled.cancel(33, 1, 9_002));
    assertEquals(LocalOrderStateStore.STATE_CANCELED, canceled.stateByOrderId(9_002));
    assertFalse(canceled.fill(33, 2, 9_002, 0));
  }

  public void testDuplicateAndStaleEventsAreNoOpsWhileNewCrossSessionEventIsAccepted() {
    LocalOrderStateStore store = placedStore(1_001, 9_001, 1_000);

    assertTrue(store.amend(11, 2, 9_001, 900));
    assertFalse(store.amend(11, 2, 9_001, 800));
    assertFalse(store.amend(11, 1, 9_001, 800));
    assertEquals(900, store.remainingQuantity(9_001));
    assertTrue(store.amend(22, 1, 9_001, 800));
    assertEquals(800, store.remainingQuantity(9_001));
    assertFalse(store.amend(11, 1, 9_001, 700));
    assertTrue(store.amend(11, 3, 9_001, 700));
  }

  public void testAmendAfterPartialFillDoesNotReopenTheOrder() {
    LocalOrderStateStore store = placedStore(1_001, 9_001, 1_000);
    assertTrue(store.fill(11, 2, 9_001, 600));
    assertTrue(store.amend(22, 1, 9_001, 500));
    assertEquals(LocalOrderStateStore.STATE_PARTIALLY_FILLED, store.stateByOrderId(9_001));
    assertEquals(500, store.remainingQuantity(9_001));
  }

  public void testPendingRejectIsTerminalAndIdentifiersRemainExact() {
    LocalOrderStateStore store = new LocalOrderStateStore(2);
    long clientOrderId = Long.MAX_VALUE;
    long instrumentId = Long.MIN_VALUE + 1;
    assertTrue(store.registerPending(clientOrderId, instrumentId, 2, -123, 5));
    assertTrue(store.reject(44, 1, clientOrderId));
    assertEquals(LocalOrderStateStore.STATE_REJECTED, store.stateByClientOrderId(clientOrderId));
    assertEquals(instrumentId, store.instrumentIdByClientOrderId(clientOrderId));
    assertFalse(store.place(44, 2, clientOrderId, Long.MAX_VALUE - 1, 5));
  }

  public void testCapacityRequiresExplicitTerminalReleaseAndNeverAliasesIds() {
    LocalOrderStateStore store = new LocalOrderStateStore(1);
    assertTrue(store.registerPending(1, 10, 1, 20, 30));
    assertThrows(IllegalStateException.class, () -> store.registerPending(2, 10, 1, 20, 30));
    assertFalse(store.releaseTerminalByClientOrderId(1));
    assertTrue(store.reject(1, 1, 1));
    assertTrue(store.releaseTerminalByClientOrderId(1));
    assertEquals(0, store.size());
    assertTrue(store.registerPending(2, 10, 1, 20, 30));
    assertEquals(LocalOrderStateStore.STATE_EMPTY, store.stateByClientOrderId(1));
  }

  public void testInvalidInputFailsBeforeMutation() {
    assertThrows(IllegalArgumentException.class, () -> new LocalOrderStateStore(0));
    LocalOrderStateStore store = new LocalOrderStateStore(1);
    assertThrows(
        IllegalArgumentException.class,
        () -> store.registerPending(Long.MIN_VALUE, 1, 1, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> store.registerPending(1, 1, 0, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> store.registerPending(1, 1, 1, 1, 0));
    assertEquals(0, store.size());
  }

  public void testWarmedLifecycleAllocatesNothing() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    LocalOrderStateStore store = new LocalOrderStateStore(1);
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      exercise(store, iteration + 1L);
    }
    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(store, 1_000_001L + iteration);
    }
    assertEquals(
        0L,
        bean.getThreadAllocatedBytes(threadId) - before,
        "local order-state lifecycle allocated bytes");
  }

  private static LocalOrderStateStore placedStore(
      long clientOrderId, long orderId, long quantity) {
    LocalOrderStateStore store = new LocalOrderStateStore(4);
    assertTrue(store.registerPending(clientOrderId, 5_001, 1, 20, quantity));
    assertTrue(store.place(11, 1, clientOrderId, orderId, quantity));
    return store;
  }

  private static void exercise(LocalOrderStateStore store, long clientOrderId) {
    long orderId = -clientOrderId;
    if (!store.registerPending(clientOrderId, 1, 1, 2, 3)
        || !store.place(1, 1, clientOrderId, orderId, 3)
        || !store.cancel(1, 2, orderId)
        || !store.releaseTerminalByClientOrderId(clientOrderId)) {
      throw new AssertionError("local order lifecycle failed");
    }
    sink = orderId;
  }
}
