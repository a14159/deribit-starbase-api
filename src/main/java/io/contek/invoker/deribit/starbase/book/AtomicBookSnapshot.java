package io.contek.invoker.deribit.starbase.book;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import io.contek.invoker.deribit.starbase.marketdata.SnapshotSynchronization;
import java.util.Objects;

/**
 * Double-buffered book snapshot coordinator with fixed-capacity incremental replay.
 *
 * <p>The active reference changes only after the snapshot and all contiguous buffered
 * incrementals have applied successfully to the staging book.
 */
public final class AtomicBookSnapshot {

  private static final byte PUT = 1;
  private static final byte REDUCE = 2;
  private static final byte DELETE = 3;

  private final SnapshotSynchronization synchronization = new SnapshotSynchronization();
  private final byte[] operation;
  private final long[] sequence;
  private final long[] orderId;
  private final long[] instrumentId;
  private final byte[] side;
  private final long[] quantity;
  private final long[] price;
  private final long[] sortOrderId;

  private volatile AggregatedL3Book active;
  private AggregatedL3Book staging;
  private int bufferedCount;
  private boolean snapshotOpen;
  private volatile long publicationVersion;

  public AtomicBookSnapshot(
      int orderCapacity,
      int levelCapacity,
      int incrementalBufferCapacity,
      InstrumentRegistry instruments) {
    if (incrementalBufferCapacity < 1) {
      throw new IllegalArgumentException("incrementalBufferCapacity must be positive");
    }
    Objects.requireNonNull(instruments, "instruments");
    active = new AggregatedL3Book(orderCapacity, levelCapacity, instruments);
    staging = new AggregatedL3Book(orderCapacity, levelCapacity, instruments);
    operation = new byte[incrementalBufferCapacity];
    sequence = new long[incrementalBufferCapacity];
    orderId = new long[incrementalBufferCapacity];
    instrumentId = new long[incrementalBufferCapacity];
    side = new byte[incrementalBufferCapacity];
    quantity = new long[incrementalBufferCapacity];
    price = new long[incrementalBufferCapacity];
    sortOrderId = new long[incrementalBufferCapacity];
  }

  public int beginSnapshot(long incrementalAnchor) {
    int result = synchronization.beginSnapshot(incrementalAnchor);
    staging.clear();
    bufferedCount = 0;
    snapshotOpen = true;
    return result;
  }

  public int snapshotPut(
      long orderId,
      long instrumentId,
      int side,
      long quantity,
      long price,
      long sortOrderId) {
    requireSnapshot();
    return staging.put(orderId, instrumentId, side, quantity, price, sortOrderId);
  }

  public int snapshotReduce(
      long orderId, long instrumentId, int side, long remainingQuantity) {
    requireSnapshot();
    return staging.reduce(orderId, instrumentId, side, remainingQuantity);
  }

  public int snapshotDelete(long orderId, long instrumentId, int side) {
    requireSnapshot();
    return staging.delete(orderId, instrumentId, side);
  }

  public int bufferPut(
      long sequence,
      long orderId,
      long instrumentId,
      int side,
      long quantity,
      long price,
      long sortOrderId) {
    int result = acceptBuffered(sequence);
    if (result == SnapshotSynchronization.BUFFER) {
      int slot = reserve();
      operation[slot] = PUT;
      storeIdentity(slot, sequence, orderId, instrumentId, side);
      this.quantity[slot] = quantity;
      this.price[slot] = price;
      this.sortOrderId[slot] = sortOrderId;
    }
    return result;
  }

  public int bufferReduce(
      long sequence,
      long orderId,
      long instrumentId,
      int side,
      long remainingQuantity) {
    int result = acceptBuffered(sequence);
    if (result == SnapshotSynchronization.BUFFER) {
      int slot = reserve();
      operation[slot] = REDUCE;
      storeIdentity(slot, sequence, orderId, instrumentId, side);
      quantity[slot] = remainingQuantity;
    }
    return result;
  }

  public int bufferDelete(
      long sequence, long orderId, long instrumentId, int side) {
    int result = acceptBuffered(sequence);
    if (result == SnapshotSynchronization.BUFFER) {
      int slot = reserve();
      operation[slot] = DELETE;
      storeIdentity(slot, sequence, orderId, instrumentId, side);
    }
    return result;
  }

  public int completeSnapshot(long incrementalAnchor) {
    requireSnapshot();
    try {
      int result = synchronization.completeSnapshot(incrementalAnchor);
      if (result == SnapshotSynchronization.RECOVER) {
        throw new StarbaseProtocolException(
            "snapshot has a non-contiguous incremental gap");
      }
      if (result == SnapshotSynchronization.REPLAY_REQUIRED) {
        replayBuffered();
        synchronization.completeReplay(sequence[bufferedCount - 1] + 1);
      }
      long nextPublicationVersion = Math.incrementExact(publicationVersion);
      AggregatedL3Book previous = active;
      active = staging;
      staging = previous;
      publicationVersion = nextPublicationVersion;
      snapshotOpen = false;
      bufferedCount = 0;
      return SnapshotSynchronization.LIVE;
    } catch (RuntimeException exception) {
      snapshotOpen = false;
      synchronization.reset();
      bufferedCount = 0;
      throw exception;
    }
  }

  public void failSnapshot() {
    requireSnapshot();
    snapshotOpen = false;
    bufferedCount = 0;
    staging.clear();
    synchronization.reset();
  }

  public AggregatedL3Book activeBook() {
    return active;
  }

  public long publicationVersion() {
    return publicationVersion;
  }

  public long nextExpectedSequence() {
    return synchronization.nextExpectedSequence();
  }

  private int acceptBuffered(long value) {
    requireSnapshot();
    int result = synchronization.onIncremental(value);
    if (result == SnapshotSynchronization.RECOVER) {
      snapshotOpen = false;
      bufferedCount = 0;
      synchronization.reset();
      throw new StarbaseProtocolException("non-contiguous incremental during snapshot");
    }
    return result;
  }

  private int reserve() {
    if (bufferedCount == operation.length) {
      synchronization.reset();
      snapshotOpen = false;
      bufferedCount = 0;
      throw new IllegalStateException("snapshot incremental buffer capacity exhausted");
    }
    return bufferedCount++;
  }

  private void storeIdentity(
      int slot, long sequence, long orderId, long instrumentId, int side) {
    this.sequence[slot] = sequence;
    this.orderId[slot] = orderId;
    this.instrumentId[slot] = instrumentId;
    this.side[slot] = (byte) side;
  }

  private void replayBuffered() {
    for (int slot = 0; slot < bufferedCount; slot++) {
      switch (operation[slot]) {
        case PUT ->
            staging.put(
                orderId[slot],
                instrumentId[slot],
                side[slot],
                quantity[slot],
                price[slot],
                sortOrderId[slot]);
        case REDUCE ->
            staging.reduce(
                orderId[slot], instrumentId[slot], side[slot], quantity[slot]);
        case DELETE ->
            staging.delete(orderId[slot], instrumentId[slot], side[slot]);
        default -> throw new StarbaseProtocolException("unknown buffered book operation");
      }
    }
  }

  private void requireSnapshot() {
    if (!snapshotOpen) {
      throw new StarbaseProtocolException("no active atomic book snapshot");
    }
  }
}
