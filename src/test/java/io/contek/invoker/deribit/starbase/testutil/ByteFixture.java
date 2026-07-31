package io.contek.invoker.deribit.starbase.testutil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Fluent deterministic wire fixture backed by one little-endian buffer. */
public final class ByteFixture {

  private final ByteBuffer buffer;

  private ByteFixture(ByteBuffer buffer) {
    this.buffer = buffer.order(ByteOrder.LITTLE_ENDIAN);
  }

  public static ByteFixture allocate(int capacity) {
    return new ByteFixture(ByteBuffer.allocate(capacity));
  }

  public static ByteFixture allocateDirect(int capacity) {
    return new ByteFixture(ByteBuffer.allocateDirect(capacity));
  }

  public ByteFixture putByte(int offset, int value) {
    buffer.put(offset, (byte) value);
    return this;
  }

  public ByteFixture putShort(int offset, int value) {
    buffer.putShort(offset, (short) value);
    return this;
  }

  public ByteFixture putInt(int offset, int value) {
    buffer.putInt(offset, value);
    return this;
  }

  public ByteFixture putLong(int offset, long value) {
    buffer.putLong(offset, value);
    return this;
  }

  public ByteFixture fill(int offset, int length, int value) {
    for (int index = 0; index < length; index++) {
      buffer.put(offset + index, (byte) value);
    }
    return this;
  }

  public ByteBuffer buffer() {
    return buffer;
  }
}
