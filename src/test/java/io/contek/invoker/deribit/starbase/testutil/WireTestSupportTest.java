package io.contek.invoker.deribit.starbase.testutil;

import static io.contek.invoker.deribit.starbase.testutil.BufferAssertions.assertBytes;
import static io.contek.invoker.deribit.starbase.testutil.BufferAssertions.assertTruncated;
import static io.contek.invoker.deribit.starbase.testutil.BufferAssertions.assertZeroPadding;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertSame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WireTestSupportTest {
  public void testFixtureUsesLittleEndianAbsoluteOffsetsWithoutMovingPosition() {
    ByteFixture fixture =
        ByteFixture.allocate(24)
            .putShort(3, 0x1234)
            .putInt(7, 0x12345678)
            .putLong(12, 0x0102030405060708L);

    ByteBuffer buffer = fixture.buffer();

    assertSame(ByteOrder.LITTLE_ENDIAN, buffer.order());
    assertEquals(0, buffer.position());
    assertBytes(buffer, 3, 0x34, 0x12);
    assertBytes(buffer, 7, 0x78, 0x56, 0x34, 0x12);
    assertBytes(buffer, 12, 0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01);
    assertZeroPadding(buffer, 0, 3);
  }
  public void testTruncationHelperChecksEveryShorterLimitAndRestoresOriginalLimit() {
    ByteBuffer buffer = ByteFixture.allocate(8).putLong(0, 42L).buffer();

    assertTruncated(buffer, 0, 8, IndexOutOfBoundsException.class, candidate -> candidate.getLong(0));

    assertEquals(8, buffer.limit());
    assertEquals(42L, buffer.getLong(0));
  }
  public void testExplicitPaddingCanBeFilledAndVerified() {
    ByteBuffer buffer = ByteFixture.allocate(16).fill(5, 3, 0x7F).buffer();

    assertBytes(buffer, 5, 0x7F, 0x7F, 0x7F);
    assertZeroPadding(buffer, 8, 8);
  }
}
