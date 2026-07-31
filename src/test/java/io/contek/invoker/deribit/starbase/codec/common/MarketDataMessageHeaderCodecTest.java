package io.contek.invoker.deribit.starbase.codec.common;

import static io.contek.invoker.deribit.starbase.testutil.BufferAssertions.assertBytes;
import static io.contek.invoker.deribit.starbase.testutil.BufferAssertions.assertTruncated;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import io.contek.invoker.deribit.starbase.testutil.ByteFixture;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MarketDataMessageHeaderCodecTest {

  public void testDecoderReadsPinnedGoldenLayoutAtAbsoluteOffset() {
    ByteBuffer buffer =
        ByteFixture.allocate(40)
            .putShort(4, 24)
            .putShort(6, 0x1234)
            .putShort(8, 1)
            .putShort(
                10,
                MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION
                    | MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION)
            .putLong(12, 0x0102030405060708L)
            .buffer();

    MarketDataMessageHeaderCodec.validate(buffer, 4);

    assertBytes(buffer, 4, 0x18, 0x00, 0x34, 0x12, 0x01, 0x00, 0x03, 0x00);
    assertEquals(24, MarketDataMessageHeaderCodec.messageLength(buffer, 4));
    assertEquals(0x1234, MarketDataMessageHeaderCodec.templateId(buffer, 4));
    assertEquals(1, MarketDataMessageHeaderCodec.version(buffer, 4));
    assertEquals(3, MarketDataMessageHeaderCodec.flags(buffer, 4));
    assertTrue(MarketDataMessageHeaderCodec.isStartOfTransaction(buffer, 4));
    assertTrue(MarketDataMessageHeaderCodec.isEndOfTransaction(buffer, 4));
    assertEquals(0x0102030405060708L, MarketDataMessageHeaderCodec.transactTimeNanos(buffer, 4));
    assertEquals(0, buffer.position());
  }

  public void testTransactionFlagsAreIndependentAndMayBothBeClear() {
    ByteBuffer buffer =
        ByteFixture.allocate(16)
            .putShort(0, 16)
            .putShort(2, 119)
            .putShort(4, 1)
            .putShort(6, 0)
            .putLong(8, 9)
            .buffer();

    MarketDataMessageHeaderCodec.validate(buffer, 0);

    assertFalse(MarketDataMessageHeaderCodec.isStartOfTransaction(buffer, 0));
    assertFalse(MarketDataMessageHeaderCodec.isEndOfTransaction(buffer, 0));
  }

  public void testValidationRejectsEveryHeaderTruncationBodyTruncationLengthFlagsAndEndian() {
    ByteBuffer buffer =
        ByteFixture.allocate(32)
            .putShort(0, 20)
            .putShort(2, 20)
            .putShort(4, 1)
            .putShort(6, 0)
            .buffer();

    assertTruncated(
        buffer,
        0,
        MarketDataMessageHeaderCodec.ENCODED_LENGTH,
        StarbaseProtocolException.class,
        candidate -> MarketDataMessageHeaderCodec.validate(candidate, 0));
    buffer.limit(19);
    assertThrows(
        StarbaseProtocolException.class, () -> MarketDataMessageHeaderCodec.validate(buffer, 0));
    buffer.limit(32);
    buffer.putShort(0, (short) 15);
    assertThrows(
        StarbaseProtocolException.class, () -> MarketDataMessageHeaderCodec.validate(buffer, 0));
    buffer.putShort(0, (short) 16);
    buffer.putShort(6, (short) 4);
    assertThrows(
        StarbaseProtocolException.class, () -> MarketDataMessageHeaderCodec.validate(buffer, 0));
    buffer.putShort(6, (short) 0);
    assertThrows(
        StarbaseProtocolException.class,
        () -> MarketDataMessageHeaderCodec.validate(buffer.order(ByteOrder.BIG_ENDIAN), 0));
  }
}
