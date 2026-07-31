package io.contek.invoker.deribit.starbase.orderentry.state;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertNotEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertSame;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

public final class ClientOrderIdMapTest {

  private static volatile long sink;

  public void testCollidingStringsReceiveStableDistinctIdsWithExactReverseLookup() {
    ClientOrderIdMap map = new ClientOrderIdMap(4, 100);
    String first = "FB";
    String second = "Ea";
    assertEquals(first.hashCode(), second.hashCode());

    long firstId = map.map(first);
    long secondId = map.map(second);

    assertEquals(100, firstId);
    assertEquals(101, secondId);
    assertNotEquals(firstId, secondId);
    assertEquals(firstId, map.map(new String(first)));
    assertSame(first, map.externalId(firstId));
    assertSame(second, map.externalId(secondId));
    assertEquals(2, map.size());
  }

  public void testMappingRemainsStableUntilExplicitReleaseAndCapacityReuseGetsFreshId() {
    ClientOrderIdMap map = new ClientOrderIdMap(1, 10);
    assertEquals(10, map.map("live"));
    assertThrows(IllegalStateException.class, () -> map.map("other"));
    assertSame("live", map.externalId(10));
    assertFalse(map.release(999));
    assertTrue(map.release(10));
    assertThrows(IllegalArgumentException.class, () -> map.externalId(10));
    assertEquals(11, map.map("other"));
  }

  public void testRestartRestoresLivePairsAndContinuesFromPersistedNextId() {
    ClientOrderIdMap before = new ClientOrderIdMap(2, 100);
    assertEquals(100, before.map("live"));
    long persistedNext = before.nextNumericId();

    ClientOrderIdMap after = new ClientOrderIdMap(2, persistedNext);
    assertTrue(after.restore("live", 100));
    assertEquals(100, after.map("live"));
    assertSame("live", after.externalId(100));
    assertEquals(101, after.map("new"));
    assertFalse(after.restore("live", 100));
    assertThrows(IllegalArgumentException.class, () -> after.restore("different", 100));
  }

  public void testNumericIdExhaustionAndInvalidInputsFailExplicitly() {
    assertThrows(IllegalArgumentException.class, () -> new ClientOrderIdMap(0, 1));
    assertThrows(IllegalArgumentException.class, () -> new ClientOrderIdMap(1, 0));
    ClientOrderIdMap map = new ClientOrderIdMap(1, Long.MAX_VALUE);
    assertThrows(IllegalArgumentException.class, () -> map.map(null));
    assertThrows(IllegalArgumentException.class, () -> map.map(""));
    assertEquals(Long.MAX_VALUE, map.map("maximum"));
    assertTrue(map.release(Long.MAX_VALUE));
    assertThrows(IllegalStateException.class, () -> map.map("exhausted"));
    assertThrows(IllegalArgumentException.class, () -> map.externalId(1));
  }

  public void testExhaustedStateSurvivesRestartWithoutReissuingLongMaxValue() {
    ClientOrderIdMap before = new ClientOrderIdMap(1, Long.MAX_VALUE);
    assertEquals(Long.MAX_VALUE, before.map("maximum"));
    assertTrue(before.isIdExhausted());

    ClientOrderIdMap after =
        new ClientOrderIdMap(1, before.nextNumericId(), before.isIdExhausted());
    assertTrue(after.restore("maximum", Long.MAX_VALUE));
    assertTrue(after.release(Long.MAX_VALUE));
    assertThrows(IllegalStateException.class, () -> after.map("never-reissue"));
  }

  public void testWarmedExistingForwardAndReverseLookupAllocateNothing() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ClientOrderIdMap map = new ClientOrderIdMap(4, 1);
    String externalId = "stable-client-id";
    long numericId = map.map(externalId);
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      exercise(map, externalId, numericId);
    }
    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(map, externalId, numericId);
    }
    assertEquals(
        0L,
        bean.getThreadAllocatedBytes(threadId) - before,
        "client-order-ID lookup allocated bytes");
  }

  private static void exercise(ClientOrderIdMap map, String externalId, long numericId) {
    if (map.map(externalId) != numericId || map.externalId(numericId) != externalId) {
      throw new AssertionError("mapping changed");
    }
    sink = numericId;
  }
}
