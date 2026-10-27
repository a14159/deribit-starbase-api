package io.contek.invoker.deribit.starbase.marketdata;

import io.contek.invoker.deribit.starbase.channel.StarbaseBlockTradeChannel;

/** Fixed-capacity signed-long cache for block-trade channels. */
final class BlockTradeChannelRouter {

  private final int maximumEntries;
  private final int mask;
  private final boolean[] occupied;
  private final long[] keys;
  private final StarbaseBlockTradeChannel[] channels;
  private volatile int size;

  BlockTradeChannelRouter(int maximumEntries) {
    maximumEntries = requirePositive(maximumEntries);
    this.maximumEntries = maximumEntries;
    int tableSize = 2;
    while (tableSize < maximumEntries * 2L) {
      tableSize <<= 1;
    }
    mask = tableSize - 1;
    occupied = new boolean[tableSize];
    keys = new long[tableSize];
    channels = new StarbaseBlockTradeChannel[tableSize];
  }

  synchronized StarbaseBlockTradeChannel getOrCreate(long key) {
    int slot = findSlot(key);
    if (occupied[slot]) {
      return channels[slot];
    }
    if (size == maximumEntries) {
      throw new IllegalStateException("block-trade channel capacity exhausted");
    }
    StarbaseBlockTradeChannel channel = new StarbaseBlockTradeChannel();
    occupied[slot] = true;
    keys[slot] = key;
    channels[slot] = channel;
    size++;
    return channel;
  }

  StarbaseBlockTradeChannel existing(long key) {
    if (size == 0) {
      return null;
    }
    int slot = findSlot(key);
    return occupied[slot] ? channels[slot] : null;
  }

  private int findSlot(long key) {
    int slot = mix(key) & mask;
    while (occupied[slot] && keys[slot] != key) {
      slot = (slot + 1) & mask;
    }
    return slot;
  }

  private static int requirePositive(int value) {
    if (value < 1) {
      throw new IllegalArgumentException("maximumEntries must be positive");
    }
    return value;
  }

  private static int mix(long value) {
    value ^= value >>> 33;
    value *= 0xff51afd7ed558ccdL;
    value ^= value >>> 33;
    return (int) value;
  }
}
