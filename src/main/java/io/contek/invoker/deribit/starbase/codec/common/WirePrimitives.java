package io.contek.invoker.deribit.starbase.codec.common;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Stateless absolute-access wire primitives. Valid calls allocate nothing. */
public final class WirePrimitives {

  public static int align8(int length) {
    if (length < 0 || length > Integer.MAX_VALUE - 7) {
      throw new IllegalArgumentException("length cannot be aligned to 8: " + length);
    }
    return (length + 7) & ~7;
  }

  public static void requireBounds(ByteBuffer buffer, int offset, int length) {
    if ((offset | length) < 0 || offset > buffer.limit() - length) {
      throw new StarbaseProtocolException(
          "buffer range out of bounds: offset="
              + offset
              + ", length="
              + length
              + ", limit="
              + buffer.limit());
    }
  }

  public static void requireLittleEndian(ByteBuffer buffer) {
    if (buffer.order() != ByteOrder.LITTLE_ENDIAN) {
      throw new StarbaseProtocolException("wire buffer must use LITTLE_ENDIAN byte order");
    }
  }

  public static int getUInt8(ByteBuffer buffer, int offset) {
    requireLittleEndian(buffer);
    requireBounds(buffer, offset, Byte.BYTES);
    return Byte.toUnsignedInt(buffer.get(offset));
  }

  public static int getUInt16(ByteBuffer buffer, int offset) {
    requireLittleEndian(buffer);
    requireBounds(buffer, offset, Short.BYTES);
    return Short.toUnsignedInt(buffer.getShort(offset));
  }

  public static void putUInt8(ByteBuffer buffer, int offset, int value) {
    if ((value & ~0xFF) != 0) {
      throw new IllegalArgumentException("uint8 out of range: " + value);
    }
    requireLittleEndian(buffer);
    requireBounds(buffer, offset, Byte.BYTES);
    buffer.put(offset, (byte) value);
  }

  public static void putUInt16(ByteBuffer buffer, int offset, int value) {
    if ((value & ~0xFFFF) != 0) {
      throw new IllegalArgumentException("uint16 out of range: " + value);
    }
    requireLittleEndian(buffer);
    requireBounds(buffer, offset, Short.BYTES);
    buffer.putShort(offset, (short) value);
  }

  private WirePrimitives() {}
}
