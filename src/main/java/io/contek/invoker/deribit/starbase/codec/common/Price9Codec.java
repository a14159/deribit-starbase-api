package io.contek.invoker.deribit.starbase.codec.common;

import java.nio.ByteBuffer;

/** Absolute-access codec for the SBE Price9 composite, whose exponent is constant and unencoded. */
public final class Price9Codec {

  public static final int ENCODED_LENGTH = Long.BYTES;
  public static final int EXPONENT = -9;
  public static final long NULL_MANTISSA = Long.MIN_VALUE;

  public static long mantissa(ByteBuffer buffer, int offset) {
    requireBuffer(buffer, offset);
    return buffer.getLong(offset);
  }

  public static void put(ByteBuffer buffer, int offset, long mantissa) {
    requireMantissa(mantissa);
    requireBuffer(buffer, offset);
    buffer.putLong(offset, mantissa);
  }

  public static void putNull(ByteBuffer buffer, int offset) {
    requireBuffer(buffer, offset);
    buffer.putLong(offset, NULL_MANTISSA);
  }

  public static boolean isNull(ByteBuffer buffer, int offset) {
    requireBuffer(buffer, offset);
    return buffer.getLong(offset) == NULL_MANTISSA;
  }

  public static long fromDecimal72Exact(long mantissa, int exponent) {
    return Decimal72Codec.rescaleExact(mantissa, exponent, EXPONENT);
  }

  private static void requireBuffer(ByteBuffer buffer, int offset) {
    WirePrimitives.requireLittleEndian(buffer);
    WirePrimitives.requireBounds(buffer, offset, ENCODED_LENGTH);
  }

  private static void requireMantissa(long mantissa) {
    if (mantissa == NULL_MANTISSA) {
      throw new IllegalArgumentException("Long.MIN_VALUE is reserved for Price9 null");
    }
  }

  private Price9Codec() {}
}
