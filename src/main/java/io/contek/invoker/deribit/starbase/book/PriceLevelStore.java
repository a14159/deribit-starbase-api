package io.contek.invoker.deribit.starbase.book;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.util.Arrays;

/** Fixed-capacity primitive Price9 level aggregation table. */
final class PriceLevelStore {

  private static final byte EMPTY = 0;
  private static final byte OCCUPIED = 1;
  private static final byte TOMBSTONE = 2;

  private final int maximumEntries;
  private final int mask;
  private final byte[] states;
  private final long[] instrumentIds;
  private final byte[] sides;
  private final long[] prices;
  private final long[] quantities;
  private final int[] orderCounts;
  private final long[] firstSortOrderIds;
  private int size;

  PriceLevelStore(int maximumEntries) {
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
    states = new byte[tableSize];
    instrumentIds = new long[tableSize];
    sides = new byte[tableSize];
    prices = new long[tableSize];
    quantities = new long[tableSize];
    orderCounts = new int[tableSize];
    firstSortOrderIds = new long[tableSize];
  }

  void checkAdd(
      long instrumentId, int side, long price, long quantity, boolean levelWillBeFreed) {
    int slot = findSlot(instrumentId, side, price);
    if (states[slot] == OCCUPIED) {
      Math.addExact(quantities[slot], quantity);
    } else if (size == maximumEntries && !levelWillBeFreed) {
      throw new IllegalStateException("price level capacity exhausted");
    }
  }

  void checkReplace(
      long instrumentId, int side, long price, long oldQuantity, long newQuantity) {
    int slot = requireSlot(instrumentId, side, price);
    long withoutOld = Math.subtractExact(quantities[slot], oldQuantity);
    Math.addExact(withoutOld, newQuantity);
  }

  void add(long instrumentId, int side, long price, long quantity, long sortOrderId) {
    int slot = findSlot(instrumentId, side, price);
    if (states[slot] == OCCUPIED) {
      quantities[slot] = Math.addExact(quantities[slot], quantity);
      orderCounts[slot]++;
      if (sortOrderId < firstSortOrderIds[slot]) {
        firstSortOrderIds[slot] = sortOrderId;
      }
      return;
    }
    if (size == maximumEntries) {
      throw new IllegalStateException("price level capacity exhausted");
    }
    states[slot] = OCCUPIED;
    instrumentIds[slot] = instrumentId;
    sides[slot] = (byte) side;
    prices[slot] = price;
    quantities[slot] = quantity;
    orderCounts[slot] = 1;
    firstSortOrderIds[slot] = sortOrderId;
    size++;
  }

  void replaceQuantity(
      long instrumentId, int side, long price, long oldQuantity, long newQuantity) {
    int slot = requireSlot(instrumentId, side, price);
    quantities[slot] =
        Math.addExact(Math.subtractExact(quantities[slot], oldQuantity), newQuantity);
  }

  void removeOrder(long instrumentId, int side, long price, long quantity) {
    int slot = requireSlot(instrumentId, side, price);
    long remaining = Math.subtractExact(quantities[slot], quantity);
    int remainingCount = orderCounts[slot] - 1;
    if (remaining < 0 || remainingCount < 0) {
      throw new StarbaseProtocolException("price level aggregate underflow");
    }
    if (remainingCount == 0) {
      if (remaining != 0) {
        throw new StarbaseProtocolException("empty price level retains quantity");
      }
      states[slot] = TOMBSTONE;
      quantities[slot] = 0;
      orderCounts[slot] = 0;
      firstSortOrderIds[slot] = 0;
      size--;
      return;
    }
    if (remaining == 0) {
      throw new StarbaseProtocolException("non-empty price level has zero quantity");
    }
    quantities[slot] = remaining;
    orderCounts[slot] = remainingCount;
  }

  void recomputePriority(
      long instrumentId, int side, long price, L3OrderStore orders) {
    int levelSlot = findExisting(instrumentId, side, price);
    if (levelSlot < 0) {
      return;
    }
    long minimum = Long.MAX_VALUE;
    int count = 0;
    long quantity = 0;
    for (int slot = 0; slot < orders.tableCapacity(); slot++) {
      if (orders.occupiedAt(slot)
          && orders.instrumentIdAt(slot) == instrumentId
          && orders.sideAt(slot) == side
          && orders.priceMantissaAt(slot) == price) {
        minimum = Math.min(minimum, orders.sortOrderIdAt(slot));
        quantity = Math.addExact(quantity, orders.quantityMantissaAt(slot));
        count++;
      }
    }
    if (count != orderCounts[levelSlot] || quantity != quantities[levelSlot]) {
      throw new StarbaseProtocolException("L3/price-level aggregate invariant mismatch");
    }
    firstSortOrderIds[levelSlot] = minimum;
  }

  boolean contains(long instrumentId, int side, long price) {
    return findExisting(instrumentId, side, price) >= 0;
  }

  long quantity(long instrumentId, int side, long price) {
    return quantities[requireSlot(instrumentId, side, price)];
  }

  int orderCount(long instrumentId, int side, long price) {
    return orderCounts[requireSlot(instrumentId, side, price)];
  }

  long firstSortOrderId(long instrumentId, int side, long price) {
    return firstSortOrderIds[requireSlot(instrumentId, side, price)];
  }

  long bestPrice(long instrumentId, int side) {
    boolean found = false;
    long best = 0;
    for (int slot = 0; slot < states.length; slot++) {
      if (states[slot] == OCCUPIED
          && instrumentIds[slot] == instrumentId
          && sides[slot] == side) {
        if (!found
            || (side > 0 && prices[slot] > best)
            || (side < 0 && prices[slot] < best)) {
          best = prices[slot];
          found = true;
        }
      }
    }
    if (!found) {
      throw new StarbaseProtocolException("no price level for instrument/side");
    }
    return best;
  }

  int size() {
    return size;
  }

  void forEach(PriceLevelConsumer consumer) {
    if (consumer == null) {
      throw new NullPointerException("consumer");
    }
    for (int slot = 0; slot < states.length; slot++) {
      if (states[slot] == OCCUPIED) {
        consumer.onLevel(
            instrumentIds[slot],
            sides[slot],
            prices[slot],
            quantities[slot],
            orderCounts[slot],
            firstSortOrderIds[slot]);
      }
    }
  }

  void clear() {
    Arrays.fill(states, EMPTY);
    size = 0;
  }

  void validateInvariants(L3OrderStore orders) {
    int occupiedLevels = 0;
    int aggregatedOrderCount = 0;
    for (int levelSlot = 0; levelSlot < states.length; levelSlot++) {
      if (states[levelSlot] != OCCUPIED) {
        continue;
      }
      occupiedLevels++;
      if (quantities[levelSlot] <= 0 || orderCounts[levelSlot] <= 0) {
        throw new StarbaseProtocolException("invalid non-positive price-level aggregate");
      }
      long expectedQuantity = 0;
      long expectedFirstSortOrderId = Long.MAX_VALUE;
      int expectedOrderCount = 0;
      for (int orderSlot = 0; orderSlot < orders.tableCapacity(); orderSlot++) {
        if (orders.occupiedAt(orderSlot)
            && orders.instrumentIdAt(orderSlot) == instrumentIds[levelSlot]
            && orders.sideAt(orderSlot) == sides[levelSlot]
            && orders.priceMantissaAt(orderSlot) == prices[levelSlot]) {
          expectedQuantity =
              Math.addExact(expectedQuantity, orders.quantityMantissaAt(orderSlot));
          expectedFirstSortOrderId =
              Math.min(expectedFirstSortOrderId, orders.sortOrderIdAt(orderSlot));
          expectedOrderCount++;
        }
      }
      if (expectedQuantity != quantities[levelSlot]
          || expectedOrderCount != orderCounts[levelSlot]
          || expectedFirstSortOrderId != firstSortOrderIds[levelSlot]) {
        throw new StarbaseProtocolException("L3/price-level aggregate invariant mismatch");
      }
      aggregatedOrderCount = Math.addExact(aggregatedOrderCount, expectedOrderCount);
    }
    if (occupiedLevels != size || aggregatedOrderCount != orders.size()) {
      throw new StarbaseProtocolException("book aggregate cardinality invariant mismatch");
    }
    for (int orderSlot = 0; orderSlot < orders.tableCapacity(); orderSlot++) {
      if (orders.occupiedAt(orderSlot)
          && findExisting(
                  orders.instrumentIdAt(orderSlot),
                  orders.sideAt(orderSlot),
                  orders.priceMantissaAt(orderSlot))
              < 0) {
        throw new StarbaseProtocolException("L3 order has no aggregate price level");
      }
    }
  }

  private int requireSlot(long instrumentId, int side, long price) {
    int slot = findExisting(instrumentId, side, price);
    if (slot < 0) {
      throw new StarbaseProtocolException("unknown Price9 level");
    }
    return slot;
  }

  private int findExisting(long instrumentId, int side, long price) {
    int slot = hash(instrumentId, side, price) & mask;
    while (states[slot] != EMPTY) {
      if (matches(slot, instrumentId, side, price)) {
        return slot;
      }
      slot = (slot + 1) & mask;
    }
    return -1;
  }

  private int findSlot(long instrumentId, int side, long price) {
    int slot = hash(instrumentId, side, price) & mask;
    int firstTombstone = -1;
    while (states[slot] != EMPTY) {
      if (matches(slot, instrumentId, side, price)) {
        return slot;
      }
      if (states[slot] == TOMBSTONE && firstTombstone < 0) {
        firstTombstone = slot;
      }
      slot = (slot + 1) & mask;
    }
    return firstTombstone >= 0 ? firstTombstone : slot;
  }

  private boolean matches(int slot, long instrumentId, int side, long price) {
    return states[slot] == OCCUPIED
        && instrumentIds[slot] == instrumentId
        && sides[slot] == side
        && prices[slot] == price;
  }

  private static int hash(long instrumentId, int side, long price) {
    long value = instrumentId ^ Long.rotateLeft(price, 29) ^ side;
    value ^= value >>> 33;
    value *= 0xff51afd7ed558ccdL;
    value ^= value >>> 33;
    return (int) value;
  }
}
