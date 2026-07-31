package io.contek.invoker.deribit.starbase.marketdata;

import io.contek.invoker.deribit.starbase.book.AggregatedL3Book;
import io.contek.invoker.deribit.starbase.book.BookPublicationBoundary;
import io.contek.invoker.deribit.starbase.book.InstrumentRegistry;
import io.contek.invoker.deribit.starbase.book.L3OrderStore;
import io.contek.invoker.deribit.starbase.channel.StarbaseLongChannel;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidPutDecoder;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;

/** Per-instrument coherent L3 reconstruction and changed-level publication state. */
final class ReconstructedOrderBookState {

  static final long INVALIDATION_PRICE = Long.MIN_VALUE;

  private final long instrumentId;
  private final StarbaseLongChannel channel;
  private final AggregatedL3Book book;
  private final int[] changedSides;
  private final long[] changedPrices;
  private final BookPublicationBoundary boundary;
  private int changedCount;
  private boolean snapshotComplete;

  ReconstructedOrderBookState(
      long instrumentId,
      int orderCapacity,
      int levelCapacity,
      InstrumentRegistry instruments,
      StarbaseLongChannel channel) {
    this.instrumentId = instrumentId;
    this.channel = channel;
    book = new AggregatedL3Book(orderCapacity, levelCapacity, instruments);
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

  private void fail(long timestamp) {
    snapshotComplete = false;
    changedCount = 0;
    book.clear();
    boundary.reset();
    channel.publish(INVALIDATION_PRICE, 0, timestamp);
  }
}
