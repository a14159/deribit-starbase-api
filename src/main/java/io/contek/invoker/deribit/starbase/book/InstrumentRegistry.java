package io.contek.invoker.deribit.starbase.book;

import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import io.contek.invoker.deribit.starbase.codec.marketdata.InstrumentDefinitionDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.InstrumentStatusUpdateDecoder;
import java.nio.ByteBuffer;
import java.util.Objects;

/** Fixed-capacity, open-addressed registry for authoritative Starbase instrument metadata. */
public final class InstrumentRegistry {

  private final int maximumEntries;
  private final int mask;
  private final boolean[] occupied;
  private final long[] instrumentIds;
  private final String[] names;
  private final ProductGroup[] productGroups;
  private final String[] quantityAssets;
  private final String[] priceAssets;
  private final byte[] quantityExponents;
  private final long[] tickSizeMantissas;
  private final long[] minimumQuantityMantissas;
  private final byte[] instrumentFlags;
  private final byte[] instrumentTypes;
  private final byte[] statuses;
  private final boolean[] authoritativeDefinitions;
  private int size;

  public InstrumentRegistry(int maximumEntries) {
    if (maximumEntries < 1) {
      throw new IllegalArgumentException("maximumEntries must be positive");
    }
    this.maximumEntries = maximumEntries;
    int tableSize = 2;
    while (tableSize < maximumEntries * 2L) {
      if (tableSize > (1 << 29)) {
        throw new IllegalArgumentException("maximumEntries is too large");
      }
      tableSize <<= 1;
    }
    mask = tableSize - 1;
    occupied = new boolean[tableSize];
    instrumentIds = new long[tableSize];
    names = new String[tableSize];
    productGroups = new ProductGroup[tableSize];
    quantityAssets = new String[tableSize];
    priceAssets = new String[tableSize];
    quantityExponents = new byte[tableSize];
    tickSizeMantissas = new long[tableSize];
    minimumQuantityMantissas = new long[tableSize];
    instrumentFlags = new byte[tableSize];
    instrumentTypes = new byte[tableSize];
    statuses = new byte[tableSize];
    authoritativeDefinitions = new boolean[tableSize];
  }

  public void upsert(
      long instrumentId,
      String name,
      ProductGroup productGroup,
      String quantityAsset,
      String priceAsset,
      int quantityExponent,
      long tickSizeMantissa,
      long minimumQuantityMantissa,
      int flags,
      int instrumentType,
      int status) {
    validate(
        instrumentId,
        name,
        productGroup,
        quantityAsset,
        priceAsset,
        quantityExponent,
        tickSizeMantissa,
        minimumQuantityMantissa,
        flags,
        instrumentType,
        status);
    int slot = findSlot(instrumentId);
    if (occupied[slot]) {
      if (!names[slot].equals(name)) {
        throw new StarbaseProtocolException(
            "instrument ID remapped to a different name: " + instrumentId);
      }
    } else {
      int nameSlot = findName(name);
      if (nameSlot >= 0) {
        throw new StarbaseProtocolException(
            "instrument name remapped to a different ID: " + name);
      }
      if (size == maximumEntries) {
        throw new IllegalStateException("instrument registry capacity exhausted");
      }
      occupied[slot] = true;
      instrumentIds[slot] = instrumentId;
      names[slot] = name;
      size++;
    }
    productGroups[slot] = productGroup;
    quantityAssets[slot] = quantityAsset;
    priceAssets[slot] = priceAsset;
    quantityExponents[slot] = (byte) quantityExponent;
    tickSizeMantissas[slot] = tickSizeMantissa;
    minimumQuantityMantissas[slot] = minimumQuantityMantissa;
    instrumentFlags[slot] = (byte) flags;
    instrumentTypes[slot] = (byte) instrumentType;
    statuses[slot] = (byte) status;
    authoritativeDefinitions[slot] = true;
  }

  /** Bootstraps routing identity without inventing SBE-only quantity or status metadata. */
  public void bootstrapIdentity(long instrumentId, String name, ProductGroup productGroup) {
    validateBootstrapIdentity(instrumentId, name, productGroup);
    int slot = findSlot(instrumentId);
    if (occupied[slot]) {
      if (!authoritativeDefinitions[slot]) productGroups[slot] = productGroup;
      return;
    }
    occupied[slot] = true;
    instrumentIds[slot] = instrumentId;
    names[slot] = name;
    productGroups[slot] = productGroup;
    size++;
  }

  public void validateBootstrapIdentity(long instrumentId, String name, ProductGroup productGroup) {
    if (instrumentId == Long.MIN_VALUE) throw new IllegalArgumentException("instrumentId is the null sentinel");
    validateAscii(name, 128, "name");
    Objects.requireNonNull(productGroup, "productGroup");
    int slot = findSlot(instrumentId);
    if (occupied[slot]) {
      if (!names[slot].equals(name)) {
        throw new StarbaseProtocolException("instrument ID remapped to a different name: " + instrumentId);
      }
      return;
    }
    int nameSlot = findName(name);
    if (nameSlot >= 0) throw new StarbaseProtocolException("instrument name remapped to a different ID: " + name);
    if (size == maximumEntries) throw new IllegalStateException("instrument registry capacity exhausted");
  }

  public int maximumEntries() {
    return maximumEntries;
  }

  public boolean hasAuthoritativeDefinition(long instrumentId) {
    return authoritativeDefinitions[requireSlot(instrumentId)];
  }

  public void updateStatus(long instrumentId, int status) {
    validateStatus(status);
    statuses[requireSlot(instrumentId)] = (byte) status;
  }

  /** Applies one validated authoritative Starbase InstrumentDefinition reference message. */
  public void applyDefinition(
      ByteBuffer buffer, int messageOffset, ProductGroup productGroup) {
    InstrumentDefinitionDecoder.validate(buffer, messageOffset);
    upsert(
        InstrumentDefinitionDecoder.instrumentId(buffer, messageOffset),
        definitionName(buffer, messageOffset),
        productGroup,
        definitionQuantityAsset(buffer, messageOffset),
        definitionPriceAsset(buffer, messageOffset),
        InstrumentDefinitionDecoder.quantityExponent(buffer, messageOffset),
        InstrumentDefinitionDecoder.tickSizeMantissa(buffer, messageOffset),
        InstrumentDefinitionDecoder.minOrderQuantityMantissa(buffer, messageOffset),
        InstrumentDefinitionDecoder.instrumentFlags(buffer, messageOffset),
        InstrumentDefinitionDecoder.instrumentType(buffer, messageOffset),
        InstrumentDefinitionDecoder.instrumentStatus(buffer, messageOffset));
  }

  /** Applies one validated authoritative Starbase InstrumentStatusUpdate message. */
  public void applyStatus(ByteBuffer buffer, int messageOffset) {
    InstrumentStatusUpdateDecoder.validate(buffer, messageOffset);
    updateStatus(
        InstrumentStatusUpdateDecoder.instrumentId(buffer, messageOffset),
        InstrumentStatusUpdateDecoder.tradingStatus(buffer, messageOffset));
  }

  public int size() {
    return size;
  }

  public boolean contains(long instrumentId) {
    int slot = findSlot(instrumentId);
    return occupied[slot];
  }

  public long instrumentId(String name) {
    Objects.requireNonNull(name, "name");
    int slot = findName(name);
    if (slot < 0) {
      throw new StarbaseProtocolException("unknown instrument name: " + name);
    }
    return instrumentIds[slot];
  }

  public String name(long instrumentId) {
    return names[requireSlot(instrumentId)];
  }

  public ProductGroup productGroup(long instrumentId) {
    return productGroups[requireSlot(instrumentId)];
  }

  public String quantityAsset(long instrumentId) {
    return quantityAssets[requireSlot(instrumentId)];
  }

  public String priceAsset(long instrumentId) {
    return priceAssets[requireSlot(instrumentId)];
  }

  public int quantityExponent(long instrumentId) {
    return quantityExponents[requireSlot(instrumentId)];
  }

  public long tickSizeMantissa(long instrumentId) {
    return tickSizeMantissas[requireSlot(instrumentId)];
  }

  public long minimumQuantityMantissa(long instrumentId) {
    return minimumQuantityMantissas[requireSlot(instrumentId)];
  }

  public int instrumentFlags(long instrumentId) {
    return Byte.toUnsignedInt(instrumentFlags[requireSlot(instrumentId)]);
  }

  public int instrumentType(long instrumentId) {
    return instrumentTypes[requireSlot(instrumentId)];
  }

  public int status(long instrumentId) {
    return statuses[requireSlot(instrumentId)];
  }

  private int requireSlot(long instrumentId) {
    int slot = findSlot(instrumentId);
    if (!occupied[slot]) {
      throw new StarbaseProtocolException("unknown instrument ID: " + instrumentId);
    }
    return slot;
  }

  private int findSlot(long instrumentId) {
    int slot = mix(instrumentId) & mask;
    while (occupied[slot] && instrumentIds[slot] != instrumentId) {
      slot = (slot + 1) & mask;
    }
    return slot;
  }

  private int findName(String name) {
    for (int slot = 0; slot < occupied.length; slot++) {
      if (occupied[slot] && names[slot].equals(name)) {
        return slot;
      }
    }
    return -1;
  }

  private static int mix(long value) {
    value ^= value >>> 33;
    value *= 0xff51afd7ed558ccdL;
    value ^= value >>> 33;
    value *= 0xc4ceb9fe1a85ec53L;
    value ^= value >>> 33;
    return (int) value;
  }

  private static void validate(
      long instrumentId,
      String name,
      ProductGroup productGroup,
      String quantityAsset,
      String priceAsset,
      int quantityExponent,
      long tickSizeMantissa,
      long minimumQuantityMantissa,
      int flags,
      int instrumentType,
      int status) {
    if (instrumentId == Long.MIN_VALUE) {
      throw new IllegalArgumentException("instrumentId is the null sentinel");
    }
    validateAscii(name, 128, "name");
    Objects.requireNonNull(productGroup, "productGroup");
    validateAscii(quantityAsset, 8, "quantityAsset");
    validateAscii(priceAsset, 8, "priceAsset");
    if (quantityExponent == Byte.MIN_VALUE
        || quantityExponent < Byte.MIN_VALUE
        || quantityExponent > Byte.MAX_VALUE) {
      throw new IllegalArgumentException("invalid quantityExponent: " + quantityExponent);
    }
    if (tickSizeMantissa == Long.MIN_VALUE
        || minimumQuantityMantissa == Long.MIN_VALUE) {
      throw new IllegalArgumentException("required quantity/price uses null sentinel");
    }
    if ((flags & ~0x07) != 0) {
      throw new IllegalArgumentException("unknown instrument flags: " + flags);
    }
    if (instrumentType < 0 || instrumentType > 5) {
      throw new IllegalArgumentException("unknown instrument type: " + instrumentType);
    }
    validateStatus(status);
  }

  private static void validateStatus(int status) {
    if (status < 0 || status > 5) {
      throw new IllegalArgumentException("unknown instrument status: " + status);
    }
  }

  private static void validateAscii(String value, int maximumLength, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank() || value.length() > maximumLength) {
      throw new IllegalArgumentException(name + " has invalid length");
    }
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character < 0x20 || character > 0x7E) {
        throw new IllegalArgumentException(name + " is not printable ASCII");
      }
    }
  }

  private static String definitionName(ByteBuffer buffer, int messageOffset) {
    int length = InstrumentDefinitionDecoder.nameLength(buffer, messageOffset);
    char[] value = new char[length];
    for (int index = 0; index < length; index++) {
      value[index] =
          (char) InstrumentDefinitionDecoder.nameByte(buffer, messageOffset, index);
    }
    return new String(value);
  }

  private static String definitionQuantityAsset(
      ByteBuffer buffer, int messageOffset) {
    int length =
        InstrumentDefinitionDecoder.quantityAssetLength(buffer, messageOffset);
    char[] value = new char[length];
    for (int index = 0; index < length; index++) {
      value[index] =
          (char)
              InstrumentDefinitionDecoder.quantityAssetByte(
                  buffer, messageOffset, index);
    }
    return new String(value);
  }

  private static String definitionPriceAsset(ByteBuffer buffer, int messageOffset) {
    int length = InstrumentDefinitionDecoder.priceAssetLength(buffer, messageOffset);
    char[] value = new char[length];
    for (int index = 0; index < length; index++) {
      value[index] =
          (char)
              InstrumentDefinitionDecoder.priceAssetByte(
                  buffer, messageOffset, index);
    }
    return new String(value);
  }
}
