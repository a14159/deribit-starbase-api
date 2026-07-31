package io.contek.invoker.deribit.starbase.codec.common;

import java.nio.ByteBuffer;

/** Absolute-access codec and exact arithmetic for the SBE Decimal72 composite. */
public final class Decimal72Codec {

  public static final int ENCODED_LENGTH = Long.BYTES + Byte.BYTES;
  public static final long NULL_MANTISSA = Long.MIN_VALUE;
  public static final int NULL_EXPONENT = Byte.MIN_VALUE;

  public static long mantissa(ByteBuffer buffer, int offset) {
    requireBuffer(buffer, offset);
    return buffer.getLong(offset);
  }

  public static int exponent(ByteBuffer buffer, int offset) {
    requireBuffer(buffer, offset);
    return buffer.get(offset + Long.BYTES);
  }

  public static void put(ByteBuffer buffer, int offset, long mantissa, int exponent) {
    requireValue(mantissa, exponent);
    requireBuffer(buffer, offset);
    buffer.putLong(offset, mantissa);
    buffer.put(offset + Long.BYTES, (byte) exponent);
  }

  public static void putNull(ByteBuffer buffer, int offset) {
    requireBuffer(buffer, offset);
    buffer.putLong(offset, NULL_MANTISSA);
    buffer.put(offset + Long.BYTES, (byte) NULL_EXPONENT);
  }

  public static boolean isNull(ByteBuffer buffer, int offset) {
    requireBuffer(buffer, offset);
    return buffer.getLong(offset) == NULL_MANTISSA
        && buffer.get(offset + Long.BYTES) == (byte) NULL_EXPONENT;
  }

  /**
   * Changes a Decimal72 exponent without rounding.
   *
   * @throws ArithmeticException when the result overflows or would lose precision
   */
  public static long rescaleExact(long mantissa, int fromExponent, int toExponent) {
    requireValue(mantissa, fromExponent);
    requireExponent(toExponent);
    if (mantissa == 0 || fromExponent == toExponent) {
      return mantissa;
    }

    int difference = fromExponent - toExponent;
    if (difference > 0) {
      long result = mantissa;
      for (int index = 0; index < difference; index++) {
        result = Math.multiplyExact(result, 10);
      }
      return result;
    }

    long result = mantissa;
    for (int index = 0; index > difference; index--) {
      if (result % 10 != 0) {
        throw new ArithmeticException("decimal rescale would lose precision");
      }
      result /= 10;
    }
    return result;
  }

  private static void requireBuffer(ByteBuffer buffer, int offset) {
    WirePrimitives.requireLittleEndian(buffer);
    WirePrimitives.requireBounds(buffer, offset, ENCODED_LENGTH);
  }

  private static void requireValue(long mantissa, int exponent) {
    requireExponent(exponent);
    if (mantissa == NULL_MANTISSA) {
      throw new IllegalArgumentException("Long.MIN_VALUE is reserved for Decimal72 null");
    }
  }

  private static void requireExponent(int exponent) {
    if (exponent <= NULL_EXPONENT || exponent > Byte.MAX_VALUE) {
      throw new IllegalArgumentException("Decimal72 exponent out of range: " + exponent);
    }
  }

  private Decimal72Codec() {}
}
