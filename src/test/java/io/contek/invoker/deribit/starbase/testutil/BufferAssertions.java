package io.contek.invoker.deribit.starbase.testutil;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import java.nio.ByteBuffer;

/** Wire-focused assertions shared by codec tests. */
public final class BufferAssertions {

  @FunctionalInterface
  public interface BufferRead {
    void read(ByteBuffer buffer);
  }

  public static void assertBytes(ByteBuffer buffer, int offset, int... expectedUnsigned) {
    for (int index = 0; index < expectedUnsigned.length; index++) {
      assertEquals(
          expectedUnsigned[index],
          Byte.toUnsignedInt(buffer.get(offset + index)),
          "byte at absolute offset " + (offset + index));
    }
  }

  public static void assertZeroPadding(ByteBuffer buffer, int offset, int length) {
    for (int index = 0; index < length; index++) {
      assertEquals(0, buffer.get(offset + index), "padding at absolute offset " + (offset + index));
    }
  }

  public static void assertTruncated(
      ByteBuffer buffer,
      int messageOffset,
      int encodedLength,
      Class<? extends Throwable> expectedFailure,
      BufferRead read) {
    int originalPosition = buffer.position();
    int originalLimit = buffer.limit();
    try {
      for (int length = 0; length < encodedLength; length++) {
        buffer.limit(messageOffset + length);
        assertThrows(
            expectedFailure,
            () -> read.read(buffer),
            "accepted truncated length " + length + " of " + encodedLength);
      }
    } finally {
      buffer.limit(originalLimit);
      buffer.position(originalPosition);
    }
  }

  private BufferAssertions() {}
}
