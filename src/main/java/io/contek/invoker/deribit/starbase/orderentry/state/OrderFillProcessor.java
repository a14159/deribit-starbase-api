package io.contek.invoker.deribit.starbase.orderentry.state;

import java.util.Objects;

/** Bounded primitive match-ID de-duplication shared by response and unsolicited fills. */
public final class OrderFillProcessor {

  private final LocalOrderStateStore orders;
  private final long[] matchIds;
  private final byte[] occupied;
  private final FillListener listener;
  private int size;

  public OrderFillProcessor(
      LocalOrderStateStore orders, int matchCapacity, FillListener listener) {
    this.orders = Objects.requireNonNull(orders, "orders");
    if (matchCapacity < 1) {
      throw new IllegalArgumentException("matchCapacity must be positive");
    }
    matchIds = new long[matchCapacity];
    occupied = new byte[matchCapacity];
    this.listener = Objects.requireNonNull(listener, "listener");
  }

  public synchronized boolean onImmediateFill(
      long sessionId, long matchId, long orderId, long fillQuantity) {
    return process(sessionId, matchId, orderId, fillQuantity);
  }

  public synchronized boolean onUnsolicitedFill(
      long sessionId, long matchId, long orderId, long fillQuantity) {
    return process(sessionId, matchId, orderId, fillQuantity);
  }

  /** Records a fill already reflected in an authoritative response remaining quantity. */
  public synchronized boolean onAuthoritativeFill(
      long sessionId,
      long matchId,
      long orderId,
      long fillQuantity,
      long remainingQuantity) {
    if (find(matchId) >= 0) {
      return false;
    }
    if (fillQuantity < 1 || orders.remainingQuantity(orderId) != remainingQuantity) {
      return false;
    }
    int slot = emptySlot();
    if (slot < 0) {
      throw new IllegalStateException("fill match-ID capacity exhausted");
    }
    matchIds[slot] = matchId;
    occupied[slot] = 1;
    size++;
    listener.onFill(sessionId, matchId, orderId, fillQuantity, remainingQuantity);
    return true;
  }

  public synchronized int size() {
    return size;
  }

  public synchronized boolean containsMatchId(long matchId) {
    return find(matchId) >= 0;
  }

  /** Clears prior-session match IDs only after local orders have been authoritatively reconciled. */
  public synchronized void resetAfterReconciliation() {
    for (int slot = 0; slot < occupied.length; slot++) {
      occupied[slot] = 0;
      matchIds[slot] = 0;
    }
    size = 0;
  }

  private boolean process(long sessionId, long matchId, long orderId, long fillQuantity) {
    if (find(matchId) >= 0) {
      return false;
    }
    int slot = emptySlot();
    if (slot < 0) {
      throw new IllegalStateException("fill match-ID capacity exhausted");
    }
    if (!orders.applyFillQuantity(sessionId, orderId, fillQuantity)) {
      return false;
    }
    matchIds[slot] = matchId;
    occupied[slot] = 1;
    size++;
    long remaining = orders.remainingQuantity(orderId);
    listener.onFill(sessionId, matchId, orderId, fillQuantity, remaining);
    return true;
  }

  private int find(long matchId) {
    for (int slot = 0; slot < occupied.length; slot++) {
      if (occupied[slot] != 0 && matchIds[slot] == matchId) {
        return slot;
      }
    }
    return -1;
  }

  private int emptySlot() {
    for (int slot = 0; slot < occupied.length; slot++) {
      if (occupied[slot] == 0) {
        return slot;
      }
    }
    return -1;
  }
}
