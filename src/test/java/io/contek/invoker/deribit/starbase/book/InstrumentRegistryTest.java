package io.contek.invoker.deribit.starbase.book;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class InstrumentRegistryTest {

  private static final long LARGE_ID = 5_000_000_123L;
  private static volatile long sink;

  public void testPreservesExactLongIdentityNameProductAndUnitMetadata() {
    InstrumentRegistry registry = new InstrumentRegistry(4);

    registry.upsert(
        LARGE_ID,
        "BTC-31DEC27",
        ProductGroup.BTC,
        "BTC",
        "USD",
        -8,
        500_000L,
        1_000L,
        3,
        2,
        1);

    assertEquals(1, registry.size());
    assertTrue(registry.contains(LARGE_ID));
    assertEquals(LARGE_ID, registry.instrumentId("BTC-31DEC27"));
    assertEquals("BTC-31DEC27", registry.name(LARGE_ID));
    assertEquals(ProductGroup.BTC, registry.productGroup(LARGE_ID));
    assertEquals("BTC", registry.quantityAsset(LARGE_ID));
    assertEquals("USD", registry.priceAsset(LARGE_ID));
    assertEquals(-8, registry.quantityExponent(LARGE_ID));
    assertEquals(500_000L, registry.tickSizeMantissa(LARGE_ID));
    assertEquals(1_000L, registry.minimumQuantityMantissa(LARGE_ID));
    assertEquals(3, registry.instrumentFlags(LARGE_ID));
    assertEquals(2, registry.instrumentType(LARGE_ID));
    assertEquals(1, registry.status(LARGE_ID));
  }

  public void testAuthoritativeDefinitionUpdatesMetadataWithoutChangingIdentity() {
    InstrumentRegistry registry = new InstrumentRegistry(2);
    registry.upsert(
        42L, "ETH-PERPETUAL", ProductGroup.ETH, "ETH", "USD", -7, 10L, 2L, 1, 0, 1);

    registry.upsert(
        42L, "ETH-PERPETUAL", ProductGroup.ETH, "ETH", "USDC", -6, 20L, 3L, 5, 0, 2);
    registry.updateStatus(42L, 4);

    assertEquals(1, registry.size());
    assertEquals("USDC", registry.priceAsset(42L));
    assertEquals(-6, registry.quantityExponent(42L));
    assertEquals(20L, registry.tickSizeMantissa(42L));
    assertEquals(3L, registry.minimumQuantityMantissa(42L));
    assertEquals(5, registry.instrumentFlags(42L));
    assertEquals(4, registry.status(42L));
  }

  public void testAppliesAuthoritativeDefinitionAndStatusWireMessages() {
    InstrumentRegistry registry = new InstrumentRegistry(2);
    ByteBuffer definition = definitionMessage(8_000_000_001L, "SOL-PERPETUAL");

    registry.applyDefinition(definition, 0, ProductGroup.TIER_2);
    registry.applyStatus(statusMessage(8_000_000_001L, 5), 0);

    assertEquals(8_000_000_001L, registry.instrumentId("SOL-PERPETUAL"));
    assertEquals("SOL", registry.quantityAsset(8_000_000_001L));
    assertEquals("USD", registry.priceAsset(8_000_000_001L));
    assertEquals(-4, registry.quantityExponent(8_000_000_001L));
    assertEquals(25_000_000L, registry.tickSizeMantissa(8_000_000_001L));
    assertEquals(100L, registry.minimumQuantityMantissa(8_000_000_001L));
    assertEquals(5, registry.status(8_000_000_001L));
  }

  public void testCollisionsCapacityAndUnknownReferencesFailClosed() {
    InstrumentRegistry registry = new InstrumentRegistry(2);
    registry.upsert(
        1L, "ONE", ProductGroup.TIER_2, "SOL", "USD", -4, 1L, 1L, 0, 0, 0);
    registry.upsert(
        17L, "SEVENTEEN", ProductGroup.TIER_3, "XRP", "USD", -2, 2L, 2L, 0, 1, 1);

    assertEquals(1L, registry.instrumentId("ONE"));
    assertEquals(17L, registry.instrumentId("SEVENTEEN"));
    assertThrows(
        IllegalStateException.class,
        () ->
            registry.upsert(
                33L, "FULL", ProductGroup.BTC, "BTC", "USD", -8, 1L, 1L, 0, 0, 0));
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            registry.upsert(
                1L, "RENAMED", ProductGroup.TIER_2, "SOL", "USD", -4, 1L, 1L, 0, 0, 0));
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            registry.upsert(
                99L, "ONE", ProductGroup.TIER_2, "SOL", "USD", -4, 1L, 1L, 0, 0, 0));
    assertThrows(StarbaseProtocolException.class, () -> registry.status(999L));
    assertThrows(StarbaseProtocolException.class, () -> registry.instrumentId("UNKNOWN"));
  }

  public void testInvalidNullSentinelsUnitsAndEnumsAreRejected() {
    InstrumentRegistry registry = new InstrumentRegistry(1);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            registry.upsert(
                Long.MIN_VALUE,
                "BAD",
                ProductGroup.BTC,
                "BTC",
                "USD",
                -8,
                1L,
                1L,
                0,
                0,
                0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            registry.upsert(
                1L, " ", ProductGroup.BTC, "BTC", "USD", -8, 1L, 1L, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            registry.upsert(
                1L,
                "BAD",
                ProductGroup.BTC,
                "",
                "USD",
                Byte.MIN_VALUE,
                Long.MIN_VALUE,
                Long.MIN_VALUE,
                8,
                6,
                6));
    assertFalse(registry.contains(1L));
  }

  public void testLongIdLookupAllocatesNothingAfterWarmup() {
    InstrumentRegistry registry = new InstrumentRegistry(4);
    registry.upsert(
        LARGE_ID,
        "BTC-31DEC27",
        ProductGroup.BTC,
        "BTC",
        "USD",
        -8,
        1L,
        1L,
        0,
        1,
        1);
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    long threadId = Thread.currentThread().threadId();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      sink += registry.tickSizeMantissa(LARGE_ID);
    }
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      sink += registry.tickSizeMantissa(LARGE_ID);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated);
  }

  private static ByteBuffer definitionMessage(long instrumentId, String name) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(284).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(0, (short) 284);
    buffer.putShort(2, (short) 10);
    buffer.putShort(4, (short) 1);
    int body = 16;
    buffer.putLong(body, instrumentId);
    putAscii(buffer, body + 8, name);
    buffer.putLong(body + 136, 77L);
    putAscii(buffer, body + 208, "SOL");
    putAscii(buffer, body + 216, "USD");
    buffer.putLong(body + 224, Long.MIN_VALUE);
    buffer.putLong(body + 232, Long.MIN_VALUE);
    buffer.putLong(body + 240, 100L);
    buffer.putLong(body + 248, 25_000_000L);
    buffer.put(body + 256, (byte) -4);
    buffer.put(body + 257, (byte) 0);
    buffer.put(body + 258, (byte) 3);
    buffer.put(body + 259, (byte) 1);
    buffer.putShort(276, (short) 16);
    buffer.putShort(278, (short) 0);
    buffer.putShort(280, (short) 9);
    buffer.putShort(282, (short) 0);
    return buffer;
  }

  private static ByteBuffer statusMessage(long instrumentId, int status) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(25).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(0, (short) 25);
    buffer.putShort(2, (short) 16);
    buffer.putShort(4, (short) 1);
    buffer.putLong(16, instrumentId);
    buffer.put(24, (byte) status);
    return buffer;
  }

  private static void putAscii(ByteBuffer buffer, int offset, String value) {
    for (int index = 0; index < value.length(); index++) {
      buffer.put(offset + index, (byte) value.charAt(index));
    }
  }
}
