package io.contek.invoker.deribit.starbase.codec.common;

import static io.contek.invoker.deribit.starbase.testutil.BufferAssertions.assertBytes;
import static io.contek.invoker.deribit.starbase.testutil.BufferAssertions.assertTruncated;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import io.contek.invoker.deribit.starbase.testutil.ByteFixture;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class UdpPacketHeaderCodecTest {

  public void testRetransmitEncoderWritesGoldenAbsoluteLayoutAndDecoderReadsAllFields() {
    ByteBuffer buffer = ByteFixture.allocate(40).fill(0, 40, 0x66).buffer();

    UdpPacketHeaderCodec.encode(
        buffer,
        3,
        0x0102030405060708L,
        0x1112131415161718L,
        0x12345678,
        UdpPacketHeaderCodec.TYPE_RETRANSMIT,
        1);

    assertEquals(0, buffer.position());
    assertBytes(buffer, 3, 0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01);
    assertBytes(buffer, 11, 0x18, 0x17, 0x16, 0x15, 0x14, 0x13, 0x12, 0x11);
    assertBytes(buffer, 19, 0x78, 0x56, 0x34, 0x12, 0x04, 0x00, 0x01, 0x00);
    assertEquals(0x0102030405060708L, UdpPacketHeaderCodec.sendingTimeNanos(buffer, 3));
    assertEquals(0x1112131415161718L, UdpPacketHeaderCodec.sequenceNumber(buffer, 3));
    assertEquals(0x12345678, UdpPacketHeaderCodec.channelId(buffer, 3));
    assertEquals(UdpPacketHeaderCodec.TYPE_RETRANSMIT, UdpPacketHeaderCodec.type(buffer, 3));
    assertEquals(1, UdpPacketHeaderCodec.messageCount(buffer, 3));
  }

  public void testZeroMessageHeartbeatIsValidAndRetainsNextExpectedSequence() {
    ByteBuffer buffer = ByteFixture.allocate(24).buffer();
    UdpPacketHeaderCodec.encode(
        buffer, 0, 99L, 1234L, -7, UdpPacketHeaderCodec.TYPE_INCREMENTAL_UPDATE, 0);

    UdpPacketHeaderCodec.validate(buffer, 0);

    assertEquals(1234L, UdpPacketHeaderCodec.sequenceNumber(buffer, 0));
    assertEquals(0, UdpPacketHeaderCodec.messageCount(buffer, 0));
  }

  public void testValidationRejectsTruncationEndianAndInvalidPacketTypeBits() {
    ByteBuffer buffer = ByteFixture.allocate(24).buffer();
    UdpPacketHeaderCodec.encode(
        buffer, 0, 1, 2, 3, UdpPacketHeaderCodec.TYPE_SNAPSHOT, 4);

    assertTruncated(
        buffer,
        0,
        UdpPacketHeaderCodec.ENCODED_LENGTH,
        StarbaseProtocolException.class,
        candidate -> UdpPacketHeaderCodec.validate(candidate, 0));
    assertEquals(24, buffer.limit());

    buffer.putShort(
        UdpPacketHeaderCodec.TYPE_OFFSET,
        (short)
            (UdpPacketHeaderCodec.TYPE_INCREMENTAL_UPDATE
                | UdpPacketHeaderCodec.TYPE_SNAPSHOT));
    assertThrows(StarbaseProtocolException.class, () -> UdpPacketHeaderCodec.validate(buffer, 0));
    buffer.putShort(UdpPacketHeaderCodec.TYPE_OFFSET, (short) UdpPacketHeaderCodec.TYPE_SNAPSHOT);
    assertThrows(
        StarbaseProtocolException.class,
        () -> UdpPacketHeaderCodec.validate(buffer.order(ByteOrder.BIG_ENDIAN), 0));
  }

  public void testEncoderRejectsInvalidTypeAndUnsignedMessageCount() {
    ByteBuffer buffer = ByteFixture.allocate(24).buffer();

    assertThrows(
        IllegalArgumentException.class,
        () -> UdpPacketHeaderCodec.encode(buffer, 0, 1, 2, 3, 8, 1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            UdpPacketHeaderCodec.encode(
                buffer, 0, 1, 2, 3, UdpPacketHeaderCodec.TYPE_RETRANSMIT, 65_536));
  }

  public void testDocumentedRetransmitSuccessAndRejectControlPacketTypesAreValid() {
    ByteBuffer buffer = ByteFixture.allocate(24).buffer();

    UdpPacketHeaderCodec.encode(
        buffer,
        0,
        1,
        2,
        3,
        UdpPacketHeaderCodec.TYPE_INCREMENTAL_UPDATE | UdpPacketHeaderCodec.TYPE_RETRANSMIT,
        1);
    UdpPacketHeaderCodec.validate(buffer, 0);

    UdpPacketHeaderCodec.encode(
        buffer, 0, 1, 2, 3, UdpPacketHeaderCodec.TYPE_CONTROL, 1);
    UdpPacketHeaderCodec.validate(buffer, 0);
  }
}
