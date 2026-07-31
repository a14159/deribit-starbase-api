package io.contek.invoker.deribit.starbase.orderentry.state;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

public final class CorrelationTableTest {

  private static volatile long sink;

  public void testPrimitiveCorrelationRetainsCommandIdentityUntilCompletedAndReleased() {
    CorrelationTable table = new CorrelationTable(4, 100);

    long correlationId = table.register(7, 9001, 1_000, 50);

    assertEquals(100, correlationId);
    assertEquals(CorrelationTable.STATE_PENDING, table.state(correlationId));
    assertEquals(7, table.commandType(correlationId));
    assertEquals(9001, table.clientOrderId(correlationId));
    assertEquals(1_050, table.deadlineNanos(correlationId));
    assertTrue(table.complete(correlationId, 3, 8001));
    assertEquals(CorrelationTable.STATE_COMPLETED, table.state(correlationId));
    assertEquals(3, table.resultCode(correlationId));
    assertEquals(8001, table.orderId(correlationId));
    assertTrue(table.release(correlationId));
    assertEquals(0, table.size());
  }

  public void testExactTimeoutIsTerminalAndLateResponseCannotMutateTheSlot() {
    CorrelationTable table = new CorrelationTable(2, 1);
    long correlationId = table.register(1, 10, 100, 20);
    assertEquals(0, table.expireNext(119));
    assertEquals(correlationId, table.expireNext(120));
    assertEquals(CorrelationTable.STATE_TIMED_OUT, table.state(correlationId));
    assertFalse(table.complete(correlationId, 7, 99));
    assertEquals(0, table.resultCode(correlationId));
    assertTrue(table.release(correlationId));
    assertFalse(table.complete(correlationId, 7, 99));
  }

  public void testCapacityExhaustionRequiresReleaseAndReuseGetsANewCorrelationId() {
    CorrelationTable table = new CorrelationTable(2, 10);
    long first = table.register(1, 1, 0, 10);
    long second = table.register(2, 2, 0, 10);
    assertThrows(IllegalStateException.class, () -> table.register(3, 3, 0, 10));
    assertEquals(2, table.size());
    assertTrue(table.release(first));
    long reused = table.register(3, 3, 0, 10);
    assertEquals(12, reused);
    assertEquals(2, table.size());
    assertEquals(CorrelationTable.STATE_PENDING, table.state(second));
  }

  public void testExpiryScanFindsEachDuePendingSlotButNeverCompletedSlots() {
    CorrelationTable table = new CorrelationTable(3, 1);
    long first = table.register(1, 1, 0, 5);
    long second = table.register(1, 2, 0, 10);
    long third = table.register(1, 3, 0, 5);
    table.complete(first, 1, 11);
    assertEquals(third, table.expireNext(5));
    assertEquals(0, table.expireNext(5));
    assertEquals(second, table.expireNext(10));
    assertEquals(CorrelationTable.STATE_COMPLETED, table.state(first));
  }

  public void testInvalidArgumentsDeadlineSaturationAndIdExhaustionFailExplicitly() {
    assertThrows(IllegalArgumentException.class, () -> new CorrelationTable(0, 1));
    assertThrows(IllegalArgumentException.class, () -> new CorrelationTable(1, 0));
    CorrelationTable table = new CorrelationTable(1, Long.MAX_VALUE);
    assertThrows(
        IllegalArgumentException.class,
        () -> table.register(0, 1, 0, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> table.register(1, Long.MIN_VALUE, 0, 1));
    long maximum = table.register(1, 1, Long.MAX_VALUE - 5, 10);
    assertEquals(Long.MAX_VALUE, maximum);
    assertEquals(Long.MAX_VALUE, table.deadlineNanos(maximum));
    table.release(maximum);
    assertThrows(IllegalStateException.class, () -> table.register(1, 1, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> table.commandType(123));
  }

  public void testRegisterCompleteReleaseHotPathAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    CorrelationTable table = new CorrelationTable(4, 1);
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      exercise(table, iteration);
    }
    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(table, iteration);
    }
    assertEquals(
        0L,
        bean.getThreadAllocatedBytes(threadId) - before,
        "correlation table hot path allocated bytes");
  }

  private static void exercise(CorrelationTable table, long clientOrderId) {
    long correlationId = table.register(1, clientOrderId, 0, 1);
    if (!table.complete(correlationId, 0, clientOrderId)
        || !table.release(correlationId)) {
      throw new AssertionError("correlation lifecycle failed");
    }
    sink = correlationId;
  }
}
