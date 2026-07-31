package io.contek.invoker.deribit.starbase.channel;

/** Fixed-capacity primitive long-key cache for stable channel identity. */
public final class PrimitiveLongChannelRouter {

  private final int maximumEntries;
  private final int mask;
  private final boolean[] occupied;
  private final long[] keys;
  private final StarbaseLongChannel[] channels;
  private volatile int size;

  public PrimitiveLongChannelRouter(int maximumEntries) {
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
    keys = new long[tableSize];
    channels = new StarbaseLongChannel[tableSize];
  }

  public synchronized StarbaseLongChannel getOrCreate(long key) {
    if (key == Long.MIN_VALUE) {
      throw new IllegalArgumentException("channel key is the null sentinel");
    }
    int slot = findSlot(key);
    if (occupied[slot]) {
      return channels[slot];
    }
    if (size == maximumEntries) {
      throw new IllegalStateException("channel routing capacity exhausted");
    }
    StarbaseLongChannel channel = new StarbaseLongChannel();
    occupied[slot] = true;
    keys[slot] = key;
    channels[slot] = channel;
    size++;
    return channel;
  }

  public StarbaseLongChannel existing(long key) {
    if (key == Long.MIN_VALUE) {
      return null;
    }
    if (size == 0) {
      return null;
    }
    int slot = findSlot(key);
    return occupied[slot] ? channels[slot] : null;
  }

  public void publishIfPresent(long key, long value, long timestampNanos) {
    StarbaseLongChannel channel = existing(key);
    if (channel != null) {
      channel.publish(key, value, timestampNanos);
    }
  }

  public int size() {
    return size;
  }

  private int findSlot(long key) {
    int slot = mix(key) & mask;
    while (occupied[slot] && keys[slot] != key) {
      slot = (slot + 1) & mask;
    }
    return slot;
  }

  private static int mix(long value) {
    value ^= value >>> 33;
    value *= 0xff51afd7ed558ccdL;
    value ^= value >>> 33;
    value *= 0xc4ceb9fe1a85ec53L;
    value ^= value >>> 33;
    return (int) value;
  }
}
