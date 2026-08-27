package io.contek.invoker.deribit.starbase.codec.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ReferenceDataDecodersTest {

  private static volatile long sink;

  public void testInstrumentDefinitionDecodesEveryFixedAndRepeatingField() {
    int length = 16 + 260 + 4 + 2 * 16 + 4 + 9;
    ByteBuffer buffer = message(length, 10);
    int body = 16;
    buffer.putLong(body, 101);
    putAscii(buffer, body + 8, 128, "BTC-PERPETUAL");
    buffer.putLong(body + 136, 202);
    putAscii(buffer, body + 144, 64, "BTC");
    putAscii(buffer, body + 208, 8, "BTC");
    putAscii(buffer, body + 216, 8, "USD");
    buffer.putLong(body + 224, Long.MIN_VALUE);
    buffer.putLong(body + 232, 12_345_000_000L);
    buffer.putLong(body + 240, 10);
    buffer.putLong(body + 248, 500_000L);
    buffer.put(body + 256, (byte) -3);
    buffer.put(body + 257, (byte) 0);
    buffer.put(body + 258, (byte) 5);
    buffer.put(body + 259, (byte) 0);
    int largeDimensions = body + 260;
    buffer.putShort(largeDimensions, (short) 16);
    buffer.putShort(largeDimensions + 2, (short) 2);
    buffer.putLong(largeDimensions + 4, 1_000_000L);
    buffer.putLong(largeDimensions + 12, 10_000_000_000L);
    buffer.putLong(largeDimensions + 20, 2_000_000L);
    buffer.putLong(largeDimensions + 28, 20_000_000_000L);
    int legDimensions = largeDimensions + 36;
    buffer.putShort(legDimensions, (short) 9);
    buffer.putShort(legDimensions + 2, (short) 1);
    buffer.putLong(legDimensions + 4, 303);
    buffer.put(legDimensions + 12, (byte) -2);

    InstrumentDefinitionDecoder.validate(buffer, 0);

    assertEquals(101, InstrumentDefinitionDecoder.instrumentId(buffer, 0));
    assertEquals(13, InstrumentDefinitionDecoder.nameLength(buffer, 0));
    assertEquals('B', InstrumentDefinitionDecoder.nameByte(buffer, 0, 0));
    assertEquals(202, InstrumentDefinitionDecoder.indexId(buffer, 0));
    assertEquals(3, InstrumentDefinitionDecoder.underlyingLength(buffer, 0));
    assertEquals('C', InstrumentDefinitionDecoder.underlyingByte(buffer, 0, 2));
    assertEquals(3, InstrumentDefinitionDecoder.quantityAssetLength(buffer, 0));
    assertEquals('U', InstrumentDefinitionDecoder.priceAssetByte(buffer, 0, 0));
    assertTrue(InstrumentDefinitionDecoder.isExpiryTimeNull(buffer, 0));
    assertEquals(12_345_000_000L, InstrumentDefinitionDecoder.strikePriceMantissa(buffer, 0));
    assertEquals(10, InstrumentDefinitionDecoder.minOrderQuantityMantissa(buffer, 0));
    assertEquals(500_000L, InstrumentDefinitionDecoder.tickSizeMantissa(buffer, 0));
    assertEquals(-3, InstrumentDefinitionDecoder.quantityExponent(buffer, 0));
    assertEquals(0, InstrumentDefinitionDecoder.instrumentType(buffer, 0));
    assertEquals(5, InstrumentDefinitionDecoder.instrumentFlags(buffer, 0));
    assertEquals(0, InstrumentDefinitionDecoder.instrumentStatus(buffer, 0));
    assertEquals(2, InstrumentDefinitionDecoder.largeTickSizeCount(buffer, 0));
    assertEquals(2_000_000L, InstrumentDefinitionDecoder.largeTickSizeMantissa(buffer, 0, 1));
    assertEquals(
        20_000_000_000L,
        InstrumentDefinitionDecoder.largeTickThresholdMantissa(buffer, 0, 1));
    assertEquals(1, InstrumentDefinitionDecoder.legCount(buffer, 0));
    assertEquals(303, InstrumentDefinitionDecoder.legInstrumentId(buffer, 0, 0));
    assertEquals(-2, InstrumentDefinitionDecoder.legRatio(buffer, 0, 0));
  }

  public void testIndexInfoReferenceAndStatusLayoutsArePinned() {
    ByteBuffer index = message(16 + 136, 11);
    index.putLong(16, 44);
    putAscii(index, 24, 128, "btc_usd");
    IndexDefinitionDecoder.validate(index, 0);
    assertEquals(44, IndexDefinitionDecoder.indexId(index, 0));
    assertEquals(7, IndexDefinitionDecoder.nameLength(index, 0));
    assertEquals('d', IndexDefinitionDecoder.nameByte(index, 0, 6));

    ByteBuffer indexInfo = message(16 + 16, 12);
    indexInfo.putLong(16, 45);
    indexInfo.putLong(24, 46);
    IndexInfoDecoder.validate(indexInfo, 0);
    assertEquals(45, IndexInfoDecoder.indexId(indexInfo, 0));
    assertEquals(46, IndexInfoDecoder.indexPriceMantissa(indexInfo, 0));

    ByteBuffer info = message(16 + 32, 14);
    for (int field = 0; field < 4; field++) {
      info.putLong(16 + field * 8, field + 1);
    }
    InstrumentInfoDecoder.validate(info, 0);
    assertEquals(1, InstrumentInfoDecoder.instrumentId(info, 0));
    assertEquals(2, InstrumentInfoDecoder.minSellPriceMantissa(info, 0));
    assertEquals(3, InstrumentInfoDecoder.maxBuyPriceMantissa(info, 0));
    assertEquals(4, InstrumentInfoDecoder.markPriceMantissa(info, 0));

    ByteBuffer reference = message(16 + 56, 15);
    reference.putLong(16, 6);
    reference.putDouble(24, 0.01);
    reference.putDouble(32, Double.NaN);
    reference.putLong(40, 7);
    reference.putLong(48, Long.MIN_VALUE);
    reference.putLong(56, 9);
    reference.putDouble(64, 10.5);
    InstrumentRefDecoder.validate(reference, 0);
    assertEquals(6, InstrumentRefDecoder.instrumentId(reference, 0));
    assertEquals(0.01, InstrumentRefDecoder.currentFunding(reference, 0));
    assertTrue(InstrumentRefDecoder.isFunding8hNull(reference, 0));
    assertEquals(false, InstrumentRefDecoder.isCurrentFundingNull(reference, 0));
    assertEquals(7, InstrumentRefDecoder.estimatedDeliveryPriceMantissa(reference, 0));
    assertTrue(InstrumentRefDecoder.isDeliveryPriceNull(reference, 0));
    assertEquals(9, InstrumentRefDecoder.settlementPriceMantissa(reference, 0));
    assertEquals(false, InstrumentRefDecoder.isSettlementPriceNull(reference, 0));
    assertEquals(10.5, InstrumentRefDecoder.openInterest(reference, 0));
    assertEquals(false, InstrumentRefDecoder.isOpenInterestNull(reference, 0));
    reference.putDouble(64, Double.NaN);
    assertTrue(InstrumentRefDecoder.isOpenInterestNull(reference, 0));

    ByteBuffer status = message(16 + 9, 16);
    status.putLong(16, 10);
    status.put(24, (byte) 5);
    InstrumentStatusUpdateDecoder.validate(status, 0);
    assertEquals(10, InstrumentStatusUpdateDecoder.instrumentId(status, 0));
    assertEquals(5, InstrumentStatusUpdateDecoder.tradingStatus(status, 0));
  }

  public void testEachDecoderRejectsWrongTemplateLengthAndCorruptGroups() {
    assertThrows(
        StarbaseProtocolException.class,
        () -> IndexInfoDecoder.validate(message(16 + 15, 12), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> InstrumentInfoDecoder.validate(message(16 + 40, 14), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> InstrumentRefDecoder.validate(message(16 + 48, 15), 0));
    assertThrows(
        StarbaseProtocolException.class,
        () -> InstrumentStatusUpdateDecoder.validate(message(16 + 8, 16), 0));

    ByteBuffer definition = message(16 + 260 + 4 + 4, 10);
    definition.putShort(16 + 260, (short) 15);
    assertThrows(
        StarbaseProtocolException.class,
        () -> InstrumentDefinitionDecoder.validate(definition, 0));
  }

  public void testUnknownReferenceEnumsAndFlagsFailClosed() {
    ByteBuffer invalidType = validEmptyDefinition();
    invalidType.put(16 + InstrumentDefinitionDecoder.INSTRUMENT_TYPE_OFFSET, (byte) 6);
    assertThrows(
        StarbaseProtocolException.class,
        () -> InstrumentDefinitionDecoder.validate(invalidType, 0));

    ByteBuffer invalidFlags = validEmptyDefinition();
    invalidFlags.put(16 + InstrumentDefinitionDecoder.INSTRUMENT_FLAGS_OFFSET, (byte) 8);
    assertThrows(
        StarbaseProtocolException.class,
        () -> InstrumentDefinitionDecoder.validate(invalidFlags, 0));

    ByteBuffer status = message(16 + 9, 16);
    status.put(24, (byte) 6);
    assertThrows(
        StarbaseProtocolException.class,
        () -> InstrumentStatusUpdateDecoder.validate(status, 0));
  }

  public void testValidReferenceDecoderHotPathAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ByteBuffer info = message(16 + 32, 14);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(info);
    }

    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(info);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated, "valid reference decoder hot path allocated bytes");
  }

  private static ByteBuffer message(int length, int templateId) {
    ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(0, (short) length);
    buffer.putShort(2, (short) templateId);
    buffer.putShort(4, (short) 1);
    return buffer;
  }

  private static ByteBuffer validEmptyDefinition() {
    ByteBuffer buffer = message(16 + 260 + 4 + 4, 10);
    buffer.putShort(16 + 260, (short) 16);
    buffer.putShort(16 + 260 + 4, (short) 9);
    return buffer;
  }

  private static void exercise(ByteBuffer info) {
    InstrumentInfoDecoder.validate(info, 0);
    sink += InstrumentInfoDecoder.instrumentId(info, 0);
    sink += InstrumentInfoDecoder.markPriceMantissa(info, 0);
  }

  private static void putAscii(ByteBuffer buffer, int offset, int length, String value) {
    for (int index = 0; index < value.length(); index++) {
      buffer.put(offset + index, (byte) value.charAt(index));
    }
    for (int index = value.length(); index < length; index++) {
      buffer.put(offset + index, (byte) 0);
    }
  }
}
