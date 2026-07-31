package io.contek.invoker.deribit.starbase.book;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;

/** Atomically coupled primitive L3 orders and Price9 aggregate levels. */
public final class AggregatedL3Book {

  private final L3OrderStore orders;
  private final PriceLevelStore levels;

  public AggregatedL3Book(
      int orderCapacity, int levelCapacity, InstrumentRegistry instruments) {
    orders = new L3OrderStore(orderCapacity, instruments);
    levels = new PriceLevelStore(levelCapacity);
  }

  public int put(
      long orderId,
      long instrumentId,
      int side,
      long quantity,
      long price,
      long sortOrderId) {
    if (!orders.contains(orderId)) {
      levels.checkAdd(instrumentId, side, price, quantity, false);
      int result = orders.put(orderId, instrumentId, side, quantity, price, sortOrderId);
      levels.add(instrumentId, side, price, quantity, sortOrderId);
      return result;
    }
    long oldInstrument = orders.instrumentId(orderId);
    int oldSide = orders.side(orderId);
    if (oldInstrument != instrumentId || oldSide != side) {
      throw new StarbaseProtocolException("aggregated put mutates order identity");
    }
    long oldQuantity = orders.quantityMantissa(orderId);
    long oldPrice = orders.priceMantissa(orderId);
    long oldSortOrderId = orders.sortOrderId(orderId);
    if (oldQuantity == quantity && oldPrice == price && oldSortOrderId == sortOrderId) {
      return L3OrderStore.DUPLICATE;
    }
    if (oldPrice == price) {
      levels.checkReplace(instrumentId, side, price, oldQuantity, quantity);
      int result = orders.put(orderId, instrumentId, side, quantity, price, sortOrderId);
      levels.replaceQuantity(instrumentId, side, price, oldQuantity, quantity);
      levels.recomputePriority(instrumentId, side, price, orders);
      return result;
    }
    boolean oldLevelWillBeFreed =
        levels.orderCount(instrumentId, side, oldPrice) == 1;
    levels.checkAdd(instrumentId, side, price, quantity, oldLevelWillBeFreed);
    int result = orders.put(orderId, instrumentId, side, quantity, price, sortOrderId);
    levels.removeOrder(instrumentId, side, oldPrice, oldQuantity);
    levels.add(instrumentId, side, price, quantity, sortOrderId);
    levels.recomputePriority(instrumentId, side, oldPrice, orders);
    levels.recomputePriority(instrumentId, side, price, orders);
    return result;
  }

  public int reduce(
      long orderId, long instrumentId, int side, long remainingQuantity) {
    long oldQuantity = orders.quantityMantissa(orderId);
    long price = orders.priceMantissa(orderId);
    if (remainingQuantity > 0) {
      levels.checkReplace(instrumentId, side, price, oldQuantity, remainingQuantity);
    }
    int result = orders.reduce(orderId, instrumentId, side, remainingQuantity);
    if (result == L3OrderStore.DUPLICATE) {
      return result;
    }
    if (result == L3OrderStore.REMOVED) {
      levels.removeOrder(instrumentId, side, price, oldQuantity);
    } else {
      levels.replaceQuantity(
          instrumentId, side, price, oldQuantity, remainingQuantity);
    }
    levels.recomputePriority(instrumentId, side, price, orders);
    return result;
  }

  public int delete(long orderId, long instrumentId, int side) {
    long oldQuantity = orders.quantityMantissa(orderId);
    long price = orders.priceMantissa(orderId);
    int result = orders.delete(orderId, instrumentId, side);
    levels.removeOrder(instrumentId, side, price, oldQuantity);
    levels.recomputePriority(instrumentId, side, price, orders);
    return result;
  }

  public boolean containsOrder(long orderId) {
    return orders.contains(orderId);
  }

  public long orderPriceMantissa(long orderId) {
    return orders.priceMantissa(orderId);
  }

  public boolean hasLevel(long instrumentId, int side, long price) {
    return levels.contains(instrumentId, side, price);
  }

  public long levelQuantity(long instrumentId, int side, long price) {
    return levels.quantity(instrumentId, side, price);
  }

  public int levelOrderCount(long instrumentId, int side, long price) {
    return levels.orderCount(instrumentId, side, price);
  }

  public long levelFirstSortOrderId(long instrumentId, int side, long price) {
    return levels.firstSortOrderId(instrumentId, side, price);
  }

  public long bestPriceMantissa(long instrumentId, int side) {
    return levels.bestPrice(instrumentId, side);
  }

  public int levelCount() {
    return levels.size();
  }

  public int orderCount() {
    return orders.size();
  }

  /** Audits every L3 order and aggregate level without creating temporary objects. */
  public void validateInvariants() {
    try {
      levels.validateInvariants(orders);
    } catch (ArithmeticException exception) {
      throw new StarbaseProtocolException("book invariant arithmetic overflow", exception);
    }
  }

  public void clear() {
    orders.clear();
    levels.clear();
  }
}
