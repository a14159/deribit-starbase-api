package io.contek.invoker.deribit.starbase.marketdata;

import io.contek.invoker.deribit.starbase.book.AggregatedL3Book;
import io.contek.invoker.deribit.starbase.book.AtomicBookSnapshot;
import io.contek.invoker.deribit.starbase.book.BookPublicationBoundary;
import io.contek.invoker.deribit.starbase.book.InstrumentRegistry;
import io.contek.invoker.deribit.starbase.book.L3OrderStore;
import io.contek.invoker.deribit.starbase.book.PriceLevelConsumer;
import io.contek.invoker.deribit.starbase.channel.StarbaseLongChannel;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidPutDecoder;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;

/** Per-instrument coherent L3 reconstruction and changed-level publication state. */
final class ReconstructedOrderBookState {

  static final long INVALIDATION_PRICE = Long.MIN_VALUE;

  private final long instrumentId;
  private final StarbaseLongChannel channel;
  private final AtomicBookSnapshot atomicSnapshot;
  private final PriceLevelConsumer snapshotPublisher;
  private AggregatedL3Book book;
  private final int[] changedSides;
  private final long[] changedPrices;
  private final BookPublicationBoundary boundary;
  private int changedCount;
  private boolean snapshotComplete;
  private boolean atomicSnapshotOpen;
  private boolean atomicSnapshotComplete;
  private boolean recoveryTransactionOpen;
  private long atomicSnapshotGeneration;
  private long snapshotPublicationTimestamp;

  ReconstructedOrderBookState(
      long instrumentId,
      int orderCapacity,
      int levelCapacity,
      InstrumentRegistry instruments,
      StarbaseLongChannel channel) {
    this.instrumentId = instrumentId;
    this.channel = channel;
    atomicSnapshot =
        new AtomicBookSnapshot(orderCapacity, levelCapacity, orderCapacity, instruments);
    book = atomicSnapshot.activeBook();
    snapshotPublisher = this::publishSnapshotLevel;
    long changedCapacity = (long) orderCapacity + levelCapacity;
    if (changedCapacity > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("changed-level capacity is too large");
    }
    changedSides = new int[(int) changedCapacity];
    changedPrices = new long[(int) changedCapacity];
    boundary = new BookPublicationBoundary(this::publishChanges);
  }

  void put(
      long orderId,
      int side,
      long quantity,
      long price,
      long sortOrderId,
      int flags,
      long sequence,
      long timestamp,
      boolean endOfCycle) {
    try {
      boolean existing = book.containsOrder(orderId);
      long oldPrice = existing ? book.orderPriceMantissa(orderId) : price;
      int result =
          book.put(orderId, instrumentId, side, quantity, price, sortOrderId);
      if (result != L3OrderStore.DUPLICATE) {
        if (existing && oldPrice != price) {
          markChanged(side, oldPrice);
        }
        markChanged(side, price);
        boundary.onMutation();
      }
      boundary.onMessageBoundary(flags, sequence, timestamp, endOfCycle);
    } catch (RuntimeException failure) {
      fail(timestamp);
      throw failure;
    }
  }

  void reduce(
      long orderId,
      int side,
      long remainingQuantity,
      int flags,
      long sequence,
      long timestamp,
      boolean endOfCycle) {
    try {
      long price = book.orderPriceMantissa(orderId);
      int result = book.reduce(orderId, instrumentId, side, remainingQuantity);
      if (result != L3OrderStore.DUPLICATE) {
        markChanged(side, price);
        boundary.onMutation();
      }
      boundary.onMessageBoundary(flags, sequence, timestamp, endOfCycle);
    } catch (RuntimeException failure) {
      fail(timestamp);
      throw failure;
    }
  }

  void delete(
      long orderId,
      int side,
      int flags,
      long sequence,
      long timestamp,
      boolean endOfCycle) {
    try {
      long price = book.orderPriceMantissa(orderId);
      book.delete(orderId, instrumentId, side);
      markChanged(side, price);
      boundary.onMutation();
      boundary.onMessageBoundary(flags, sequence, timestamp, endOfCycle);
    } catch (RuntimeException failure) {
      fail(timestamp);
      throw failure;
    }
  }

  void onEndOfCycle(int flags, long sequence, long timestamp) {
    try {
      boundary.onMessageBoundary(flags, sequence, timestamp, true);
    } catch (RuntimeException failure) {
      fail(timestamp);
      throw failure;
    }
  }

  void markSnapshotComplete() {
    snapshotComplete = true;
    if (changedCount > 0 && !boundary.isTransactionOpen()) {
      boundary.onMutation();
    }
  }

  void beginSnapshot(int flags, long sequence, long timestamp) {
    book.clear();
    changedCount = 0;
    snapshotComplete = false;
    boundary.reset();
    boundary.onMessageBoundary(flags, sequence, timestamp, false);
  }

  void completeSnapshot(int flags, long sequence, long timestamp) {
    snapshotComplete = true;
    boundary.onMessageBoundary(flags, sequence, timestamp, false);
  }

  void invalidate(long timestamp) {
    fail(timestamp);
  }

  void beginAtomicSnapshot(long incrementalAnchor, long generation) {
    atomicSnapshot.beginSnapshot(incrementalAnchor);
    atomicSnapshotOpen = true;
    atomicSnapshotComplete = false;
    recoveryTransactionOpen = false;
    atomicSnapshotGeneration = generation;
    snapshotComplete = false;
    changedCount = 0;
    boundary.reset();
  }

  void atomicSnapshotPut(
      long orderId,
      int side,
      long quantity,
      long price,
      long sortOrderId) {
    requireAtomicSnapshot();
    atomicSnapshot.snapshotPut(orderId, instrumentId, side, quantity, price, sortOrderId);
  }

  void atomicSnapshotReduce(long orderId, int side, long remainingQuantity) {
    requireAtomicSnapshot();
    atomicSnapshot.snapshotReduce(orderId, instrumentId, side, remainingQuantity);
  }

  void atomicSnapshotDelete(long orderId, int side) {
    requireAtomicSnapshot();
    atomicSnapshot.snapshotDelete(orderId, instrumentId, side);
  }

  void completeAtomicSnapshot(long incrementalAnchor) {
    requireAtomicSnapshot();
    atomicSnapshot.completeSnapshot(incrementalAnchor);
    book = atomicSnapshot.activeBook();
    atomicSnapshotOpen = false;
    atomicSnapshotComplete = true;
    changedCount = 0;
    boundary.reset();
  }

  void incrementalPutDuringRecovery(
      long sequence,
      long orderId,
      int side,
      long quantity,
      long price,
      long sortOrderId,
      int flags,
      long timestamp) {
    if (atomicSnapshotOpen) {
      atomicSnapshot.bufferPut(
          sequence, orderId, instrumentId, side, quantity, price, sortOrderId);
      trackRecoveryBoundary(flags, false);
    } else if (atomicSnapshotComplete) {
      book.put(orderId, instrumentId, side, quantity, price, sortOrderId);
      trackRecoveryBoundary(flags, false);
    } else if (snapshotComplete) {
      put(
          orderId,
          side,
          quantity,
          price,
          sortOrderId,
          flags,
          sequence,
          timestamp,
          false);
    }
  }

  void incrementalReduceDuringRecovery(
      long sequence,
      long orderId,
      int side,
      long remainingQuantity,
      int flags,
      long timestamp) {
    if (atomicSnapshotOpen) {
      atomicSnapshot.bufferReduce(sequence, orderId, instrumentId, side, remainingQuantity);
      trackRecoveryBoundary(flags, false);
    } else if (atomicSnapshotComplete) {
      book.reduce(orderId, instrumentId, side, remainingQuantity);
      trackRecoveryBoundary(flags, false);
    } else if (snapshotComplete) {
      reduce(orderId, side, remainingQuantity, flags, sequence, timestamp, false);
    }
  }

  void incrementalDeleteDuringRecovery(
      long sequence,
      long orderId,
      int side,
      int flags,
      long timestamp) {
    if (atomicSnapshotOpen) {
      atomicSnapshot.bufferDelete(sequence, orderId, instrumentId, side);
      trackRecoveryBoundary(flags, false);
    } else if (atomicSnapshotComplete) {
      book.delete(orderId, instrumentId, side);
      trackRecoveryBoundary(flags, false);
    } else if (snapshotComplete) {
      delete(orderId, side, flags, sequence, timestamp, false);
    }
  }

  void incrementalEndOfCycleDuringRecovery() {
    if ((atomicSnapshotOpen || atomicSnapshotComplete) && recoveryTransactionOpen) {
      throw new StarbaseProtocolException(
          "incremental EndOfCycle inside recovery transaction");
    }
  }

  boolean activateAtomicSnapshot(long generation, long timestamp) {
    if (!canActivateAtomicSnapshot(generation)) {
      return false;
    }
    book.validateInvariants();
    snapshotPublicationTimestamp = timestamp;
    channel.publish(INVALIDATION_PRICE, 0, timestamp);
    book.forEachLevel(snapshotPublisher);
    snapshotComplete = true;
    atomicSnapshotComplete = false;
    changedCount = 0;
    boundary.reset();
    return true;
  }

  boolean hasAtomicSnapshot(long generation) {
    return atomicSnapshotComplete && atomicSnapshotGeneration == generation;
  }

  boolean canActivateAtomicSnapshot(long generation) {
    return hasAtomicSnapshot(generation) && !recoveryTransactionOpen;
  }

  void abandonAtomicSnapshot(long timestamp) {
    if (atomicSnapshotOpen) {
      atomicSnapshot.failSnapshot();
    }
    atomicSnapshotOpen = false;
    atomicSnapshotComplete = false;
    recoveryTransactionOpen = false;
    fail(timestamp);
  }

  boolean isReady() {
    return snapshotComplete
        && !boundary.isTransactionOpen()
        && !boundary.isFailed()
        && changedCount == 0;
  }

  private void publishChanges(long version, long sequence, long timestamp) {
    if (!snapshotComplete) {
      return;
    }
    book.validateInvariants();
    for (int index = 0; index < changedCount; index++) {
      int side = changedSides[index];
      long price = changedPrices[index];
      long signedQuantity = 0;
      if (book.hasLevel(instrumentId, side, price)) {
        long quantity = book.levelQuantity(instrumentId, side, price);
        signedQuantity = side == BidPutDecoder.SIDE ? quantity : -quantity;
      }
      channel.publish(price, signedQuantity, timestamp);
    }
    changedCount = 0;
  }

  private void markChanged(int side, long price) {
    for (int index = 0; index < changedCount; index++) {
      if (changedSides[index] == side && changedPrices[index] == price) {
        return;
      }
    }
    if (changedCount == changedPrices.length) {
      throw new IllegalStateException("changed price-level capacity exhausted");
    }
    changedSides[changedCount] = side;
    changedPrices[changedCount] = price;
    changedCount++;
  }

  private void publishSnapshotLevel(
      long publishedInstrumentId,
      int side,
      long price,
      long quantity,
      int orderCount,
      long firstSortOrderId) {
    if (publishedInstrumentId != instrumentId || orderCount < 1) {
      throw new StarbaseProtocolException("invalid recovered price level identity");
    }
    long signedQuantity = side == BidPutDecoder.SIDE ? quantity : -quantity;
    channel.publish(price, signedQuantity, snapshotPublicationTimestamp);
  }

  private void trackRecoveryBoundary(int flags, boolean endOfCycle) {
    boolean starts =
        (flags & io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION)
            != 0;
    boolean ends =
        (flags & io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION)
            != 0;
    if (starts) {
      if (recoveryTransactionOpen) {
        throw new StarbaseProtocolException("nested recovery transaction");
      }
      recoveryTransactionOpen = true;
    }
    if (endOfCycle && recoveryTransactionOpen && !ends) {
      throw new StarbaseProtocolException("EndOfCycle inside recovery transaction");
    }
    if (ends) {
      if (!recoveryTransactionOpen) {
        throw new StarbaseProtocolException("recovery transaction end without start");
      }
      recoveryTransactionOpen = false;
    }
  }

  private void requireAtomicSnapshot() {
    if (!atomicSnapshotOpen) {
      throw new StarbaseProtocolException("no active recovered book snapshot");
    }
  }

  private void fail(long timestamp) {
    snapshotComplete = false;
    changedCount = 0;
    book.clear();
    boundary.reset();
    channel.publish(INVALIDATION_PRICE, 0, timestamp);
  }
}
