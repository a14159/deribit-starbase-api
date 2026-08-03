package io.contek.invoker.deribit.starbase.orderentry.state;

/**
 * Stateless conversion from public client-order IDs to Starbase's numeric client-order ID.
 *
 * <p>Native {@code long} IDs pass through unchanged. String IDs use positional base-64
 * conversion over {@link #STRING_ALPHABET}, reduced modulo {@code 2^64 - 1}. The residue-to-ID
 * conversion skips {@link Long#MIN_VALUE}, the SBE {@code int64} null sentinel, so every other
 * signed-long ID has a canonical String representation. No reverse-lookup table is retained, and
 * capacity does not depend on order count. As the complete String domain is larger than the
 * numeric domain, callers remain responsible for choosing an ID-generation scheme without
 * collisions in the set they emit.
 */
public final class ClientOrderIdMap {

  /** Generator-compatible client-order-ID characters, in their positional digit order. */
  public static final String STRING_ALPHABET =
      "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_";

  /** Deribit String labels are limited to 64 characters. */
  public static final int MAX_STRING_LENGTH = 64;

  /** Creates a stateless converter. */
  public ClientOrderIdMap() {}

  /** Returns a schema-native ID unchanged, except for the SBE {@code int64} null sentinel. */
  public long map(long clientOrderId) {
    if (clientOrderId == Long.MIN_VALUE) {
      throw new IllegalArgumentException("clientOrderId is the SBE null value");
    }
    return clientOrderId;
  }

  /**
   * Converts a supported String ID to a numeric ID without retaining any state.
   *
   * <p>Reduction uses the odd {@code 2^64 - 1} modulus rather than signed-long overflow. This
   * matters for the common {@code counter + fixed suffix} scheme: radix-64 overflow modulo a power
   * of two would otherwise discard every counter digit once the suffix is long enough.
   */
  public long map(String externalId) {
    if (externalId == null || externalId.isEmpty()) {
      throw new IllegalArgumentException("externalId must not be empty");
    }
    if (externalId.length() > MAX_STRING_LENGTH) {
      throw new IllegalArgumentException("externalId exceeds 64 characters");
    }

    long residue = 0;
    for (int index = 0; index < externalId.length(); index++) {
      residue = appendDigit(residue, digit(externalId.charAt(index), index));
    }
    return fromResidue(residue);
  }

  /**
   * Converts an ID produced by the String path back to its canonical base-64 representation.
   *
   * <p>This method allocates the returned String. A colliding or non-canonical input String, such
   * as one with leading zero digits or a positional value beyond the numeric range, cannot be
   * recovered uniquely and normalizes to the canonical representation.
   */
  public String externalId(long numericId) {
    long residue = toResidue(numericId);
    if (residue == 0) {
      return "0";
    }

    int bitLength = Long.SIZE - Long.numberOfLeadingZeros(residue);
    char[] externalId = new char[(bitLength + 5) / 6];
    int index = externalId.length;
    long remainder = residue;
    while (remainder != 0) {
      externalId[--index] = STRING_ALPHABET.charAt((int) (remainder & 63));
      remainder >>>= 6;
    }
    return new String(externalId);
  }

  private static long appendDigit(long residue, int digit) {
    // For M = 2^64 - 1, fold the carry limb back into the low limb (one's-complement sum).
    long low = residue << 6 | digit;
    long high = residue >>> 58;
    long sum = low + high;
    if (Long.compareUnsigned(sum, low) < 0) {
      sum++;
    }
    return sum == -1L ? 0 : sum;
  }

  private static long fromResidue(long residue) {
    return residue < 0 ? residue + 1 : residue;
  }

  private static long toResidue(long clientOrderId) {
    if (clientOrderId == Long.MIN_VALUE) {
      throw new IllegalArgumentException("clientOrderId is the SBE null value");
    }
    return clientOrderId < 0 ? clientOrderId - 1 : clientOrderId;
  }

  private static int digit(char character, int index) {
    if (character >= '0' && character <= '9') {
      return character - '0';
    }
    if (character >= 'a' && character <= 'z') {
      return character - 'a' + 10;
    }
    if (character >= 'A' && character <= 'Z') {
      return character - 'A' + 36;
    }
    if (character == '-') {
      return 62;
    }
    if (character == '_') {
      return 63;
    }
    throw new IllegalArgumentException(
        "unsupported client-order-ID character at index " + index + ": " + character);
  }
}
