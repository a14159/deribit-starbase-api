package io.contek.invoker.deribit.starbase.codec.common;

import static io.contek.invoker.deribit.starbase.testutil.BufferAssertions.assertBytes;
import static io.contek.invoker.deribit.starbase.testutil.BufferAssertions.assertTruncated;
import static io.contek.invoker.deribit.starbase.testutil.BufferAssertions.assertZeroPadding;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import io.contek.invoker.deribit.starbase.testutil.ByteFixture;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class TcpHeaderCodecTest {

  private static final int OFFSET = 5;

  public void testEncoderWritesGoldenHeaderAtAbsoluteOffsetAndDecoderReadsEveryField() {
    ByteBuffer buffer = ByteFixture.allocate(64).fill(0, 64, 0x7A).buffer();

    TcpHeaderCodec.encode(
        buffer,
        OFFSET,
        TcpHeaderCodec.FLAG_RESEND,
        37,
        0x1234,
        11,
        0x0102030405060708L,
        0x1112131415161718L,
        0x2122232425262728L);

    assertEquals(0, buffer.position());
    assertBytes(buffer, OFFSET, 0xDB, 0x01, 0x25, 0x00, 0x34, 0x12, 0x0B, 0x00);
    assertBytes(buffer, OFFSET + 8, 0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01);
    assertEquals(TcpHeaderCodec.PROTOCOL_ID, TcpHeaderCodec.protocolId(buffer, OFFSET));
    assertEquals(TcpHeaderCodec.FLAG_RESEND, TcpHeaderCodec.flags(buffer, OFFSET));
    assertEquals(37, TcpHeaderCodec.messageLength(buffer, OFFSET));
    assertEquals(0x1234, TcpHeaderCodec.messageTypeId(buffer, OFFSET));
    assertEquals(11, TcpHeaderCodec.version(buffer, OFFSET));
    assertEquals(0x0102030405060708L, TcpHeaderCodec.sequenceNumber(buffer, OFFSET));
    assertEquals(0x1112131415161718L, TcpHeaderCodec.lastProcessedSequenceNumber(buffer, OFFSET));
    assertEquals(0x2122232425262728L, TcpHeaderCodec.sendTimeNanos(buffer, OFFSET));
  }

  public void testFramePaddingIsZeroedAndValidatedWithoutChangingPosition() {
    ByteBuffer buffer = ByteFixture.allocate(64).fill(0, 64, 0x55).buffer();
    TcpHeaderCodec.encode(buffer, 0, 0, 37, 100, 11, 1, 0, 2);

    TcpHeaderCodec.zeroPadding(buffer, 0, 37);

    assertZeroPadding(buffer, 37, 3);
    assertEquals(0, buffer.position());
    assertEquals(40, TcpHeaderCodec.validateFrame(buffer, 0));
    buffer.put(39, (byte) 1);
    assertThrows(StarbaseProtocolException.class, () -> TcpHeaderCodec.validateFrame(buffer, 0));
  }

  public void testValidationRejectsTruncationEndianProtocolLengthFlagsAndBodyTruncation() {
    ByteBuffer buffer = ByteFixture.allocate(64).buffer();
    TcpHeaderCodec.encode(buffer, 0, 0, 40, 100, 11, 1, 0, 2);

    assertTruncated(
        buffer,
        0,
        TcpHeaderCodec.ENCODED_LENGTH,
        StarbaseProtocolException.class,
        candidate -> TcpHeaderCodec.validateHeader(candidate, 0));
    assertEquals(64, buffer.limit());

    buffer.put(0, (byte) 0);
    assertThrows(StarbaseProtocolException.class, () -> TcpHeaderCodec.validateHeader(buffer, 0));
    buffer.put(0, (byte) TcpHeaderCodec.PROTOCOL_ID);
    buffer.putShort(2, (short) 31);
    assertThrows(StarbaseProtocolException.class, () -> TcpHeaderCodec.validateHeader(buffer, 0));
    buffer.putShort(2, (short) 40);
    buffer.put(1, (byte) 0x80);
    assertThrows(StarbaseProtocolException.class, () -> TcpHeaderCodec.validateHeader(buffer, 0));
    buffer.put(1, (byte) 0);
    buffer.limit(39);
    assertThrows(StarbaseProtocolException.class, () -> TcpHeaderCodec.validateFrame(buffer, 0));
    buffer.limit(64);
    assertThrows(
        StarbaseProtocolException.class,
        () -> TcpHeaderCodec.validateHeader(buffer.order(ByteOrder.BIG_ENDIAN), 0));
  }

  public void testEncoderRejectsInvalidUnsignedFieldsLengthAndFlags() {
    ByteBuffer buffer = ByteFixture.allocate(64).buffer();

    assertThrows(
        IllegalArgumentException.class,
        () -> TcpHeaderCodec.encode(buffer, 0, 0, 31, 1, 1, 1, 0, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> TcpHeaderCodec.encode(buffer, 0, 2, 32, 1, 1, 1, 0, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> TcpHeaderCodec.encode(buffer, 0, 0, 32, 65_536, 1, 1, 0, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> TcpHeaderCodec.encode(buffer, 0, 0, 32, 1, -1, 1, 0, 1));
  }
}
