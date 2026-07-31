package io.contek.invoker.deribit.starbase.book;

import io.contek.invoker.deribit.starbase.codec.marketdata.AskPutDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.AskDeleteDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.AskQtyReducedDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidPutDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidDeleteDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidQtyReducedDecoder;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/** Fixed-capacity primitive L3 order table keyed by signed 64-bit exchange order ID. */
public final class L3OrderStore {

  public static final int INSERTED = 1;
  public static final int UPDATED = 2;
  public static final int DUPLICATE = 3;
  public static final int REDUCED = 4;
  public static final int REMOVED = 5;

  private static final byte EMPTY = 0;
  private static final byte OCCUPIED = 1;
  private static final byte TOMBSTONE = 2;

  private final InstrumentRegistry instruments;
  private final int maximumEntries;
  private final int mask;
  private final byte[] states;
  private final long[] orderIds;
  private final long[] instrumentIds;
  private final byte[] sides;
  private final long[] quantityMantissas;
  private final long[] priceMantissas;
  private final long[] sortOrderIds;
  private int size;

  public L3OrderStore(int maximumEntries, InstrumentRegistry instruments) {
    if (maximumEntries < 1) {
      throw new IllegalArgumentException("maximumEntries must be positive");
    }
    this.instruments = Objects.requireNonNull(instruments, "instruments");
    this.maximumEntries = maximumEntries;
    int tableSize = 2;
    while (tableSize < maximumEntries * 2L) {
      if (tableSize > (1 << 29)) {
        throw new IllegalArgumentException("maximumEntries is too large");
      }
      tableSize <<= 1;
    }
    mask = tableSize - 1;
    states = new byte[tableSize];
    orderIds = new long[tableSize];
    instrumentIds = new long[tableSize];
    sides = new byte[tableSize];
    quantityMantissas = new long[tableSize];
    priceMantissas = new long[tableSize];
    sortOrderIds = new long[tableSize];
  }

  public int put(
      long orderId,
      long instrumentId,
      int side,
      long quantityMantissa,
      long priceMantissa,
      long sortOrderId) {
    validateValues(
        orderId, instrumentId, side, quantityMantissa, priceMantissa, sortOrderId);
    if (!instruments.contains(instrumentId)) {
      throw new StarbaseProtocolException(
          "book put references unknown instrument ID: " + instrumentId);
    }
    int slot = findSlot(orderId);
    if (states[slot] == OCCUPIED) {
      if (instrumentIds[slot] != instrumentId || sides[slot] != side) {
        throw new StarbaseProtocolException(
            "book put attempts to mutate order identity: " + orderId);
      }
      if (quantityMantissas[slot] == quantityMantissa
          && priceMantissas[slot] == priceMantissa
          && sortOrderIds[slot] == sortOrderId) {
        return DUPLICATE;
      }
      quantityMantissas[slot] = quantityMantissa;
      priceMantissas[slot] = priceMantissa;
      sortOrderIds[slot] = sortOrderId;
      return UPDATED;
    }
    if (size == maximumEntries) {
      throw new IllegalStateException("L3 order capacity exhausted");
    }
    states[slot] = OCCUPIED;
    orderIds[slot] = orderId;
    instrumentIds[slot] = instrumentId;
    sides[slot] = (byte) side;
    quantityMantissas[slot] = quantityMantissa;
    priceMantissas[slot] = priceMantissa;
    sortOrderIds[slot] = sortOrderId;
    size++;
    return INSERTED;
  }

  public int applyBidPut(ByteBuffer buffer, int messageOffset) {
    BidPutDecoder.validate(buffer, messageOffset);
    return put(
        BidPutDecoder.orderId(buffer, messageOffset),
        BidPutDecoder.instrumentId(buffer, messageOffset),
        BidPutDecoder.SIDE,
        BidPutDecoder.quantityMantissa(buffer, messageOffset),
        BidPutDecoder.priceMantissa(buffer, messageOffset),
        BidPutDecoder.sortOrderId(buffer, messageOffset));
  }

  public int applyAskPut(ByteBuffer buffer, int messageOffset) {
    AskPutDecoder.validate(buffer, messageOffset);
    return put(
        AskPutDecoder.orderId(buffer, messageOffset),
        AskPutDecoder.instrumentId(buffer, messageOffset),
        AskPutDecoder.SIDE,
        AskPutDecoder.quantityMantissa(buffer, messageOffset),
        AskPutDecoder.priceMantissa(buffer, messageOffset),
        AskPutDecoder.sortOrderId(buffer, messageOffset));
  }

  public int reduce(
      long orderId, long instrumentId, int side, long remainingQuantityMantissa) {
    if (remainingQuantityMantissa < 0) {
      throw new IllegalArgumentException(
          "remainingQuantityMantissa must be non-negative");
    }
    int slot = requireIdentity(orderId, instrumentId, side);
    long currentQuantity = quantityMantissas[slot];
    if (remainingQuantityMantissa > currentQuantity) {
      throw new StarbaseProtocolException(
          "quantity-reduced message increases order quantity: " + orderId);
    }
    if (remainingQuantityMantissa == currentQuantity) {
      return DUPLICATE;
    }
    if (remainingQuantityMantissa == 0) {
      markDeleted(slot);
      return REMOVED;
    }
    quantityMantissas[slot] = remainingQuantityMantissa;
    return REDUCED;
  }

  public int delete(long orderId, long instrumentId, int side) {
    int slot = requireIdentity(orderId, instrumentId, side);
    markDeleted(slot);
    return REMOVED;
  }

  public int applyBidQtyReduced(ByteBuffer buffer, int messageOffset) {
    BidQtyReducedDecoder.validate(buffer, messageOffset);
    return reduce(
        BidQtyReducedDecoder.orderId(buffer, messageOffset),
        BidQtyReducedDecoder.instrumentId(buffer, messageOffset),
        BidQtyReducedDecoder.SIDE,
        BidQtyReducedDecoder.quantityMantissa(buffer, messageOffset));
  }

  public int applyAskQtyReduced(ByteBuffer buffer, int messageOffset) {
    AskQtyReducedDecoder.validate(buffer, messageOffset);
    return reduce(
        AskQtyReducedDecoder.orderId(buffer, messageOffset),
        AskQtyReducedDecoder.instrumentId(buffer, messageOffset),
        AskQtyReducedDecoder.SIDE,
        AskQtyReducedDecoder.quantityMantissa(buffer, messageOffset));
  }

  public int applyBidDelete(ByteBuffer buffer, int messageOffset) {
    BidDeleteDecoder.validate(buffer, messageOffset);
    return delete(
        BidDeleteDecoder.orderId(buffer, messageOffset),
        BidDeleteDecoder.instrumentId(buffer, messageOffset),
        BidDeleteDecoder.SIDE);
  }

  public int applyAskDelete(ByteBuffer buffer, int messageOffset) {
    AskDeleteDecoder.validate(buffer, messageOffset);
    return delete(
        AskDeleteDecoder.orderId(buffer, messageOffset),
        AskDeleteDecoder.instrumentId(buffer, messageOffset),
        AskDeleteDecoder.SIDE);
  }

  public int size() {
    return size;
  }

  void clear() {
    Arrays.fill(states, EMPTY);
    size = 0;
  }

  public boolean contains(long orderId) {
    return findExisting(orderId) >= 0;
  }

  public long instrumentId(long orderId) {
    return instrumentIds[requireSlot(orderId)];
  }

  public int side(long orderId) {
    return sides[requireSlot(orderId)];
  }

  public long quantityMantissa(long orderId) {
    return quantityMantissas[requireSlot(orderId)];
  }

  public long priceMantissa(long orderId) {
    return priceMantissas[requireSlot(orderId)];
  }

  public long sortOrderId(long orderId) {
    return sortOrderIds[requireSlot(orderId)];
  }

  int slot(long orderId) {
    return requireSlot(orderId);
  }

  void markDeleted(int slot) {
    states[slot] = TOMBSTONE;
    size--;
  }

  void quantityMantissaAt(int slot, long value) {
    quantityMantissas[slot] = value;
  }

  long quantityMantissaAt(int slot) {
    return quantityMantissas[slot];
  }

  boolean occupiedAt(int slot) {
    return states[slot] == OCCUPIED;
  }

  int tableCapacity() {
    return states.length;
  }

  long orderIdAt(int slot) {
    return orderIds[slot];
  }

  long instrumentIdAt(int slot) {
    return instrumentIds[slot];
  }

  int sideAt(int slot) {
    return sides[slot];
  }

  long priceMantissaAt(int slot) {
    return priceMantissas[slot];
  }

  long sortOrderIdAt(int slot) {
    return sortOrderIds[slot];
  }

  private int requireSlot(long orderId) {
    int slot = findExisting(orderId);
    if (slot < 0) {
      throw new StarbaseProtocolException("unknown L3 order ID: " + orderId);
    }
    return slot;
  }

  private int requireIdentity(long orderId, long instrumentId, int side) {
    if (side != BidPutDecoder.SIDE && side != AskPutDecoder.SIDE) {
      throw new IllegalArgumentException("unknown book side: " + side);
    }
    int slot = requireSlot(orderId);
    if (instrumentIds[slot] != instrumentId || sides[slot] != side) {
      throw new StarbaseProtocolException(
          "book mutation identity mismatch for order: " + orderId);
    }
    return slot;
  }

  private int findExisting(long orderId) {
    int slot = mix(orderId) & mask;
    while (states[slot] != EMPTY) {
      if (states[slot] == OCCUPIED && orderIds[slot] == orderId) {
        return slot;
      }
      slot = (slot + 1) & mask;
    }
    return -1;
  }

  private int findSlot(long orderId) {
    int slot = mix(orderId) & mask;
    int firstTombstone = -1;
    while (states[slot] != EMPTY) {
      if (states[slot] == OCCUPIED && orderIds[slot] == orderId) {
        return slot;
      }
      if (states[slot] == TOMBSTONE && firstTombstone < 0) {
        firstTombstone = slot;
      }
      slot = (slot + 1) & mask;
    }
    return firstTombstone >= 0 ? firstTombstone : slot;
  }

  private static int mix(long value) {
    value ^= value >>> 33;
    value *= 0xff51afd7ed558ccdL;
    value ^= value >>> 33;
    value *= 0xc4ceb9fe1a85ec53L;
    value ^= value >>> 33;
    return (int) value;
  }

  private static void validateValues(
      long orderId,
      long instrumentId,
      int side,
      long quantityMantissa,
      long priceMantissa,
      long sortOrderId) {
    if (orderId == Long.MIN_VALUE
        || instrumentId == Long.MIN_VALUE
        || quantityMantissa == Long.MIN_VALUE
        || priceMantissa == Long.MIN_VALUE
        || sortOrderId == Long.MIN_VALUE) {
      throw new IllegalArgumentException("book put contains a null sentinel");
    }
    if (quantityMantissa <= 0) {
      throw new IllegalArgumentException("book put quantity must be positive");
    }
    if (side != BidPutDecoder.SIDE && side != AskPutDecoder.SIDE) {
      throw new IllegalArgumentException("unknown book side: " + side);
    }
  }
}
