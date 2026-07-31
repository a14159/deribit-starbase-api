package io.contek.invoker.deribit.starbase.marketdata;

import io.contek.invoker.deribit.starbase.book.InstrumentRegistry;
import io.contek.invoker.deribit.starbase.channel.PrimitiveLongChannelRouter;

/** Fixed-capacity primitive instrument-ID cache for reconstructed book states. */
final class OrderBookStateRouter {

  private final int maximumEntries;
  private final int mask;
  private final boolean[] occupied;
  private final long[] instrumentIds;
  private final ReconstructedOrderBookState[] states;
  private int size;

  OrderBookStateRouter(int maximumEntries) {
    this.maximumEntries = maximumEntries;
    int tableSize = 2;
    while (tableSize < maximumEntries * 2L) {
      tableSize <<= 1;
    }
    mask = tableSize - 1;
    occupied = new boolean[tableSize];
    instrumentIds = new long[tableSize];
    states = new ReconstructedOrderBookState[tableSize];
  }

  synchronized ReconstructedOrderBookState configure(
      long instrumentId,
      int orderCapacity,
      int levelCapacity,
      InstrumentRegistry instruments,
      PrimitiveLongChannelRouter channels) {
    int slot = findSlot(instrumentId);
    if (occupied[slot]) {
      return states[slot];
    }
    if (!instruments.contains(instrumentId)) {
      throw new io.contek.invoker.deribit.starbase.common.StarbaseProtocolException(
          "order-book configuration references unknown instrument: " + instrumentId);
    }
    if (size == maximumEntries) {
      throw new IllegalStateException("order-book state capacity exhausted");
    }
    ReconstructedOrderBookState state =
        new ReconstructedOrderBookState(
            instrumentId,
            orderCapacity,
            levelCapacity,
            instruments,
            channels.getOrCreate(instrumentId));
    occupied[slot] = true;
    instrumentIds[slot] = instrumentId;
    states[slot] = state;
    size++;
    return state;
  }

  ReconstructedOrderBookState require(long instrumentId) {
    int slot = findSlot(instrumentId);
    if (!occupied[slot]) {
      throw new io.contek.invoker.deribit.starbase.common.StarbaseProtocolException(
          "unconfigured order book: " + instrumentId);
    }
    return states[slot];
  }

  ReconstructedOrderBookState existing(long instrumentId) {
    int slot = findSlot(instrumentId);
    return occupied[slot] ? states[slot] : null;
  }

  void onEndOfCycle(int flags, long sequence, long timestamp) {
    for (int slot = 0; slot < occupied.length; slot++) {
      if (occupied[slot]) {
        states[slot].onEndOfCycle(flags, sequence, timestamp);
      }
    }
  }

  private int findSlot(long instrumentId) {
    int slot = mix(instrumentId) & mask;
    while (occupied[slot] && instrumentIds[slot] != instrumentId) {
      slot = (slot + 1) & mask;
    }
    return slot;
  }

  private static int mix(long value) {
    value ^= value >>> 33;
    value *= 0xff51afd7ed558ccdL;
    value ^= value >>> 33;
    return (int) value;
  }
}
