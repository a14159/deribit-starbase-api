package io.contek.invoker.deribit.starbase.orderentry.state;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

public final class ClientOrderIdMapTest {

  private static volatile long sink;

  public void testNativeLongClientOrderIdsPassThroughAcrossSignedSbeRange() {
    ClientOrderIdMap map = new ClientOrderIdMap();

    assertEquals(Long.MIN_VALUE + 1, map.map(Long.MIN_VALUE + 1));
    assertEquals(-1, map.map(-1));
    assertEquals(0, map.map(0));
    assertEquals(Long.MAX_VALUE, map.map(Long.MAX_VALUE));
  }

  public void testStringIdsUsePinnedPositionalAlphabetAndModulus() {
    ClientOrderIdMap map = new ClientOrderIdMap();

    assertEquals("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_",
        ClientOrderIdMap.STRING_ALPHABET);
    assertEquals(0, map.map("0"));
    assertEquals(1, map.map("1"));
    assertEquals(35, map.map("z"));
    assertEquals(36, map.map("A"));
    assertEquals(62, map.map("-"));
    assertEquals(63, map.map("_"));
    assertEquals(64, map.map("10"));
    assertEquals(2_275, map.map("zz"));
    assertEquals(8_048_956_845_452_111_629L, map.map("0-ab-BTC-PERPETUAL"));
    assertEquals(8_048_957_120_330_018_573L, map.map("1-ab-BTC-PERPETUAL"));
    assertEquals(6_148_914_691_236_517_205L, map.map("z".repeat(64)));
    assertEquals(map.map("1-ab-BTC-PERPETUAL"),
        new ClientOrderIdMap().map(new String("1-ab-BTC-PERPETUAL")));
  }

  public void testEveryNonNullLongConvertsBackToACanonicalString() {
    ClientOrderIdMap map = new ClientOrderIdMap();

    assertEquals("0", map.externalId(0));
    assertEquals("1", map.externalId(1));
    assertEquals("z", map.externalId(35));
    assertEquals("A", map.externalId(36));
    assertEquals("-", map.externalId(62));
    assertEquals("_", map.externalId(63));
    assertEquals("10", map.externalId(64));
    assertEquals("zz", map.externalId(2_275));
    assertEquals("1", map.externalId(map.map("01")));
    assertEquals("80000000000", map.externalId(Long.MIN_VALUE + 1));
    assertEquals("f_________-", map.externalId(-1));
    assertEquals("7__________", map.externalId(Long.MAX_VALUE));

    long[] numericIds = {
        Long.MIN_VALUE + 1,
        -1,
        0,
        1,
        2_275,
        8_048_956_845_452_111_629L,
        8_048_957_120_330_018_573L,
        Long.MAX_VALUE
    };
    for (long numericId : numericIds) {
      String externalId = map.externalId(numericId);
      assertTrue(externalId.length() <= 11);
      assertEquals(numericId, map.map(externalId));
    }
  }

  public void testReductionMatchesIndependentBigIntegerReferenceAcrossCarries() {
    ClientOrderIdMap map = new ClientOrderIdMap();
    BigInteger modulus = BigInteger.ONE.shiftLeft(Long.SIZE).subtract(BigInteger.ONE);
    long state = 0x6a09e667f3bcc909L;

    for (int sample = 0; sample < 1_000; sample++) {
      int length = sample % ClientOrderIdMap.MAX_STRING_LENGTH + 1;
      char[] characters = new char[length];
      BigInteger positionalValue = BigInteger.ZERO;
      for (int index = 0; index < length; index++) {
        state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
        int digit = (int) (state >>> 58);
        characters[index] = ClientOrderIdMap.STRING_ALPHABET.charAt(digit);
        positionalValue = positionalValue.shiftLeft(6).add(BigInteger.valueOf(digit));
      }
      long residue = positionalValue.mod(modulus).longValue();
      long expectedId = residue < 0 ? residue + 1 : residue;
      assertEquals(expectedId, map.map(new String(characters)));
    }
  }

  public void testGeneratorStyleIdsRemainDistinctBeyondFormerCapacity() {
    ClientOrderIdMap map = new ClientOrderIdMap();
    Set<Long> numericIds = new HashSet<>();

    for (int counter = 0; counter < 100_000; counter++) {
      String externalId = Integer.toString(counter, 36) + "-ab-BTC-PERPETUAL";
      assertTrue(numericIds.add(map.map(externalId)), "collision at " + externalId);
    }
    assertEquals(100_000, numericIds.size());
  }

  public void testInvalidStringsAndSbeNullSentinelFailExplicitly() {
    ClientOrderIdMap map = new ClientOrderIdMap();

    assertThrows(IllegalArgumentException.class, () -> map.map(Long.MIN_VALUE));
    assertThrows(IllegalArgumentException.class, () -> map.map((String) null));
    assertThrows(IllegalArgumentException.class, () -> map.map(""));
    assertThrows(IllegalArgumentException.class, () -> map.map("not.allowed"));
    assertThrows(IllegalArgumentException.class, () -> map.map("a".repeat(65)));
    assertThrows(IllegalArgumentException.class, () -> map.externalId(Long.MIN_VALUE));
    assertEquals(map.map("1"), map.map("01"));
    assertEquals(map.map("0"), map.map("f__________"));
  }

  public void testWarmedNativeAndStringConversionAllocateNothing() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ClientOrderIdMap map = new ClientOrderIdMap();
    String externalId = "123-ab-BTC-PERPETUAL";
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
        "client-order-ID conversion allocated bytes");
  }

  private static void exercise(ClientOrderIdMap map, String externalId, long numericId) {
    if (map.map(externalId) != numericId || map.map(numericId) != numericId) {
      throw new AssertionError("mapping changed");
    }
    sink = numericId;
  }
}
