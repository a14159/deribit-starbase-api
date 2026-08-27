package io.contek.invoker.deribit.starbase.orderentry.state;

/** Fixed-capacity primitive state for live orders observed across order-entry sessions. */
public final class LocalOrderStateStore {

  public static final int STATE_EMPTY = 0;
  public static final int STATE_PENDING = 1;
  public static final int STATE_OPEN = 2;
  public static final int STATE_PARTIALLY_FILLED = 3;
  public static final int STATE_FILLED = 4;
  public static final int STATE_CANCELED = 5;
  public static final int STATE_REJECTED = 6;
  public static final int STATE_QUEUED = 7;

  static final int RECONCILIATION_MATCHED = 0;
  static final int RECONCILIATION_INVALID_IDENTITY = 1;
  static final int RECONCILIATION_DUPLICATE_IDENTITY = 2;
  static final int RECONCILIATION_REST_ONLY = 3;
  static final int RECONCILIATION_SBE_ONLY = 4;
  static final int RECONCILIATION_TERMINAL_MISMATCH = 5;
  static final int RECONCILIATION_PENDING_LOCAL = 6;

  private final byte[] states;
  private final long[] clientOrderIds;
  private final long[] orderIds;
  private final long[] instrumentIds;
  private final byte[] sides;
  private final long[] prices;
  private final long[] originalQuantities;
  private final byte[] quantityExponents;
  private final long[] remainingQuantities;
  private final long[] originSessionIds;
  private final long[] originEventSequences;
  private final long[] alternateSessionIds;
  private final long[] alternateEventSequences;
  private final long[] lastSessionIds;
  private final long[] lastEventSequences;
  private int size;

  public LocalOrderStateStore(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    states = new byte[capacity];
    clientOrderIds = new long[capacity];
    orderIds = new long[capacity];
    instrumentIds = new long[capacity];
    sides = new byte[capacity];
    prices = new long[capacity];
    originalQuantities = new long[capacity];
    quantityExponents = new byte[capacity];
    remainingQuantities = new long[capacity];
    originSessionIds = new long[capacity];
    originEventSequences = new long[capacity];
    alternateSessionIds = new long[capacity];
    alternateEventSequences = new long[capacity];
    lastSessionIds = new long[capacity];
    lastEventSequences = new long[capacity];
  }

  public synchronized boolean registerPending(
      long clientOrderId, long instrumentId, int side, long price, long quantity) {
    return registerPending(clientOrderId, instrumentId, side, price, quantity, 0);
  }

  public synchronized boolean registerPending(
      long clientOrderId,
      long instrumentId,
      int side,
      long price,
      long quantity,
      int quantityExponent) {
    requireId(clientOrderId, "clientOrderId");
    requireId(instrumentId, "instrumentId");
    if (side != 1 && side != 2) {
      throw new IllegalArgumentException("side must be 1 or 2");
    }
    if (quantity < 1) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    if (quantityExponent < -127 || quantityExponent > 127) {
      throw new IllegalArgumentException("quantityExponent must be a non-null int8");
    }
    if (findByClientId(clientOrderId) >= 0) {
      return false;
    }
    int slot = emptySlot();
    if (slot < 0) {
      throw new IllegalStateException("local order-state capacity exhausted");
    }
    states[slot] = STATE_PENDING;
    clientOrderIds[slot] = clientOrderId;
    orderIds[slot] = Long.MIN_VALUE;
    instrumentIds[slot] = instrumentId;
    sides[slot] = (byte) side;
    prices[slot] = price;
    originalQuantities[slot] = quantity;
    quantityExponents[slot] = (byte) quantityExponent;
    remainingQuantities[slot] = quantity;
    size++;
    return true;
  }

  public synchronized boolean place(
      long sessionId, long eventSequence, long clientOrderId, long orderId, long remainingQuantity) {
    requireEvent(sessionId, eventSequence);
    requireId(orderId, "orderId");
    int slot = findByClientId(clientOrderId);
    if (slot < 0 || states[slot] != STATE_PENDING || findByOrderId(orderId) >= 0) {
      return false;
    }
    if (remainingQuantity < 1 || remainingQuantity > originalQuantities[slot]) {
      throw new IllegalArgumentException("invalid remainingQuantity");
    }
    orderIds[slot] = orderId;
    remainingQuantities[slot] = remainingQuantity;
    originSessionIds[slot] = sessionId;
    recordEvent(slot, sessionId, eventSequence);
    states[slot] =
        (byte)
            (remainingQuantity == originalQuantities[slot]
                ? STATE_OPEN
                : STATE_PARTIALLY_FILLED);
    return true;
  }

  public synchronized boolean queue(
      long sessionId, long eventSequence, long clientOrderId, long orderId, long quantity) {
    requireEvent(sessionId, eventSequence);
    requireId(orderId, "orderId");
    int slot = findByClientId(clientOrderId);
    if (slot < 0 || states[slot] != STATE_PENDING || findByOrderId(orderId) >= 0) {
      return false;
    }
    if (quantity < 1 || quantity > originalQuantities[slot]) {
      throw new IllegalArgumentException("invalid queued quantity");
    }
    orderIds[slot] = orderId;
    remainingQuantities[slot] = quantity;
    originSessionIds[slot] = sessionId;
    recordEvent(slot, sessionId, eventSequence);
    states[slot] = STATE_QUEUED;
    return true;
  }

  /** Applies the authoritative status carried by the same or a later validated event. */
  public synchronized boolean applyStatus(
      long sessionId, long eventSequence, long orderId, long remainingQuantity, int status) {
    requireEvent(sessionId, eventSequence);
    int slot = findByOrderId(orderId);
    if (slot < 0
        || !(isLive(states[slot])
            || states[slot] == STATE_FILLED && status == 2
            || states[slot] == STATE_CANCELED && status == 3)
        || isBefore(slot, sessionId, eventSequence)) {
      return false;
    }
    if (remainingQuantity < 0 || remainingQuantity > originalQuantities[slot]) {
      throw new IllegalArgumentException("invalid status remainingQuantity");
    }
    int nextState =
        switch (status) {
          case 1 -> {
            if (remainingQuantity == 0) {
              throw new IllegalArgumentException("active order has no remaining quantity");
            }
            yield remainingQuantity == originalQuantities[slot]
                ? STATE_OPEN
                : STATE_PARTIALLY_FILLED;
          }
          case 2 -> {
            if (remainingQuantity != 0) {
              throw new IllegalArgumentException("filled order has remaining quantity");
            }
            yield STATE_FILLED;
          }
          case 3 -> STATE_CANCELED;
          case 4 -> {
            if (remainingQuantity == 0) {
              throw new IllegalArgumentException("queued order has no remaining quantity");
            }
            yield STATE_QUEUED;
          }
          default -> throw new IllegalArgumentException("unknown order status: " + status);
        };
    remainingQuantities[slot] = remainingQuantity;
    states[slot] = (byte) nextState;
    recordEvent(slot, sessionId, eventSequence);
    return true;
  }

  public synchronized boolean amend(
      long sessionId, long eventSequence, long orderId, long remainingQuantity) {
    requireEvent(sessionId, eventSequence);
    int slot = findByOrderId(orderId);
    if (slot < 0 || !isLive(states[slot]) || isStale(slot, sessionId, eventSequence)) {
      return false;
    }
    if (remainingQuantity < 1) {
      throw new IllegalArgumentException("invalid remainingQuantity");
    }
    int priorState = states[slot];
    if (priorState == STATE_OPEN) {
      originalQuantities[slot] = remainingQuantity;
    }
    remainingQuantities[slot] = remainingQuantity;
    states[slot] = (byte) priorState;
    recordEvent(slot, sessionId, eventSequence);
    return true;
  }

  /** Applies one authoritative amend response, including its total and terminal status. */
  public synchronized boolean amendOutcome(
      long sessionId,
      long eventSequence,
      long orderId,
      long totalQuantity,
      long remainingQuantity,
      int status) {
    requireEvent(sessionId, eventSequence);
    int slot = findByOrderId(orderId);
    if (slot < 0 || !isLive(states[slot]) || isStale(slot, sessionId, eventSequence)) {
      return false;
    }
    if (totalQuantity < 1 || remainingQuantity < 0 || remainingQuantity > totalQuantity) {
      throw new IllegalArgumentException("invalid amend outcome quantity");
    }
    int nextState =
        switch (status) {
          case 1 -> {
            if (remainingQuantity == 0) {
              throw new IllegalArgumentException("active amend has no remaining quantity");
            }
            yield remainingQuantity == totalQuantity ? STATE_OPEN : STATE_PARTIALLY_FILLED;
          }
          case 2 -> {
            if (remainingQuantity != 0) {
              throw new IllegalArgumentException("filled amend has remaining quantity");
            }
            yield STATE_FILLED;
          }
          case 3 -> STATE_CANCELED;
          case 4 -> {
            if (remainingQuantity == 0) {
              throw new IllegalArgumentException("queued amend has no remaining quantity");
            }
            yield STATE_QUEUED;
          }
          default -> throw new IllegalArgumentException("unknown amend status: " + status);
        };
    originalQuantities[slot] = totalQuantity;
    remainingQuantities[slot] = remainingQuantity;
    states[slot] = (byte) nextState;
    recordEvent(slot, sessionId, eventSequence);
    return true;
  }

  public synchronized boolean fill(
      long sessionId, long eventSequence, long orderId, long remainingQuantity) {
    requireEvent(sessionId, eventSequence);
    int slot = findByOrderId(orderId);
    if (slot < 0 || !isLive(states[slot]) || isStale(slot, sessionId, eventSequence)) {
      return false;
    }
    if (remainingQuantity < 0 || remainingQuantity > remainingQuantities[slot]) {
      throw new IllegalArgumentException("invalid remainingQuantity");
    }
    if (remainingQuantity == remainingQuantities[slot]) {
      return false;
    }
    remainingQuantities[slot] = remainingQuantity;
    states[slot] = (byte) (remainingQuantity == 0 ? STATE_FILLED : STATE_PARTIALLY_FILLED);
    recordEvent(slot, sessionId, eventSequence);
    return true;
  }

  /** Applies one already de-duplicated fill quantity and records its observing session. */
  public synchronized boolean applyFillQuantity(
      long sessionId, long orderId, long fillQuantity) {
    requireId(sessionId, "sessionId");
    int slot = findByOrderId(orderId);
    if (slot < 0 || !isLive(states[slot])) {
      return false;
    }
    if (fillQuantity < 1 || fillQuantity > remainingQuantities[slot]) {
      throw new IllegalArgumentException("invalid fillQuantity");
    }
    long remaining = remainingQuantities[slot] - fillQuantity;
    remainingQuantities[slot] = remaining;
    states[slot] = (byte) (remaining == 0 ? STATE_FILLED : STATE_PARTIALLY_FILLED);
    lastSessionIds[slot] = sessionId;
    return true;
  }

  public synchronized boolean cancel(long sessionId, long eventSequence, long orderId) {
    requireEvent(sessionId, eventSequence);
    int slot = findByOrderId(orderId);
    if (slot < 0 || !isLive(states[slot]) || isStale(slot, sessionId, eventSequence)) {
      return false;
    }
    states[slot] = STATE_CANCELED;
    recordEvent(slot, sessionId, eventSequence);
    return true;
  }

  public synchronized boolean reject(
      long sessionId, long eventSequence, long clientOrderId) {
    requireEvent(sessionId, eventSequence);
    int slot = findByClientId(clientOrderId);
    if (slot < 0 || states[slot] != STATE_PENDING) {
      return false;
    }
    states[slot] = STATE_REJECTED;
    originSessionIds[slot] = sessionId;
    recordEvent(slot, sessionId, eventSequence);
    return true;
  }

  public synchronized boolean releaseTerminalByClientOrderId(long clientOrderId) {
    int slot = findByClientId(clientOrderId);
    if (slot < 0 || !isTerminal(states[slot])) {
      return false;
    }
    clear(slot);
    size--;
    return true;
  }

  public synchronized int stateByOrderId(long orderId) {
    int slot = findByOrderId(orderId);
    return slot < 0 ? STATE_EMPTY : states[slot];
  }

  public synchronized int stateByClientOrderId(long clientOrderId) {
    int slot = findByClientId(clientOrderId);
    return slot < 0 ? STATE_EMPTY : states[slot];
  }

  public synchronized long instrumentIdByClientOrderId(long clientOrderId) {
    int slot = findByClientId(clientOrderId);
    if (slot < 0) {
      throw new IllegalArgumentException("unknown clientOrderId: " + clientOrderId);
    }
    return instrumentIds[slot];
  }

  public synchronized long clientOrderId(long orderId) {
    return clientOrderIds[requiredOrderSlot(orderId)];
  }

  public synchronized long instrumentId(long orderId) {
    return instrumentIds[requiredOrderSlot(orderId)];
  }

  public synchronized long originSessionId(long orderId) {
    return originSessionIds[requiredOrderSlot(orderId)];
  }

  public synchronized long lastSessionId(long orderId) {
    return lastSessionIds[requiredOrderSlot(orderId)];
  }

  public synchronized long remainingQuantity(long orderId) {
    return remainingQuantities[requiredOrderSlot(orderId)];
  }

  public synchronized long originalQuantity(long orderId) {
    return originalQuantities[requiredOrderSlot(orderId)];
  }

  public synchronized int quantityExponent(long orderId) {
    return quantityExponents[requiredOrderSlot(orderId)];
  }

  public synchronized int quantityExponentByClientOrderId(long clientOrderId) {
    int slot = findByClientId(clientOrderId);
    if (slot < 0) {
      throw new IllegalArgumentException("unknown clientOrderId: " + clientOrderId);
    }
    return quantityExponents[slot];
  }

  public synchronized int size() {
    return size;
  }

  synchronized int capacity() {
    return states.length;
  }

  synchronized int compareOpenOrderIds(long[] snapshotOrderIds, int count) {
    if (snapshotOrderIds == null || count < 0 || count > snapshotOrderIds.length) {
      throw new IllegalArgumentException("invalid open-order snapshot identity range");
    }
    for (int index = 0; index < count; index++) {
      long orderId = snapshotOrderIds[index];
      if (orderId == Long.MIN_VALUE) {
        return RECONCILIATION_INVALID_IDENTITY;
      }
      for (int prior = 0; prior < index; prior++) {
        if (snapshotOrderIds[prior] == orderId) {
          return RECONCILIATION_DUPLICATE_IDENTITY;
        }
      }
      int slot = findByOrderId(orderId);
      if (slot < 0) {
        return RECONCILIATION_REST_ONLY;
      }
      if (isTerminal(states[slot])) {
        return RECONCILIATION_TERMINAL_MISMATCH;
      }
      if (!isLive(states[slot])) {
        return RECONCILIATION_REST_ONLY;
      }
    }
    for (int slot = 0; slot < states.length; slot++) {
      int state = states[slot];
      if (state == STATE_PENDING) {
        return RECONCILIATION_PENDING_LOCAL;
      }
      if (isLive(state) && !contains(snapshotOrderIds, count, orderIds[slot])) {
        return RECONCILIATION_SBE_ONLY;
      }
    }
    return RECONCILIATION_MATCHED;
  }

  private void recordEvent(int slot, long sessionId, long eventSequence) {
    if (originSessionIds[slot] == sessionId) {
      originEventSequences[slot] = eventSequence;
    } else {
      alternateSessionIds[slot] = sessionId;
      alternateEventSequences[slot] = eventSequence;
    }
    lastSessionIds[slot] = sessionId;
    lastEventSequences[slot] = eventSequence;
  }

  private boolean isStale(int slot, long sessionId, long eventSequence) {
    if (originSessionIds[slot] == sessionId) {
      return eventSequence <= originEventSequences[slot];
    }
    return alternateEventSequences[slot] != 0
        && alternateSessionIds[slot] == sessionId
        && eventSequence <= alternateEventSequences[slot];
  }

  private boolean isBefore(int slot, long sessionId, long eventSequence) {
    if (originSessionIds[slot] == sessionId) {
      return eventSequence < originEventSequences[slot];
    }
    return alternateEventSequences[slot] != 0
        && alternateSessionIds[slot] == sessionId
        && eventSequence < alternateEventSequences[slot];
  }

  private int requiredOrderSlot(long orderId) {
    int slot = findByOrderId(orderId);
    if (slot < 0) {
      throw new IllegalArgumentException("unknown orderId: " + orderId);
    }
    return slot;
  }

  private int findByClientId(long clientOrderId) {
    for (int slot = 0; slot < states.length; slot++) {
      if (states[slot] != STATE_EMPTY && clientOrderIds[slot] == clientOrderId) {
        return slot;
      }
    }
    return -1;
  }

  private int findByOrderId(long orderId) {
    if (orderId == Long.MIN_VALUE) {
      return -1;
    }
    for (int slot = 0; slot < states.length; slot++) {
      if (states[slot] != STATE_EMPTY && orderIds[slot] == orderId) {
        return slot;
      }
    }
    return -1;
  }

  private int emptySlot() {
    for (int slot = 0; slot < states.length; slot++) {
      if (states[slot] == STATE_EMPTY) {
        return slot;
      }
    }
    return -1;
  }

  private void clear(int slot) {
    states[slot] = STATE_EMPTY;
    clientOrderIds[slot] = 0;
    orderIds[slot] = 0;
    instrumentIds[slot] = 0;
    sides[slot] = 0;
    prices[slot] = 0;
    originalQuantities[slot] = 0;
    quantityExponents[slot] = 0;
    remainingQuantities[slot] = 0;
    originSessionIds[slot] = 0;
    originEventSequences[slot] = 0;
    alternateSessionIds[slot] = 0;
    alternateEventSequences[slot] = 0;
    lastSessionIds[slot] = 0;
    lastEventSequences[slot] = 0;
  }

  private static boolean isLive(int state) {
    return state == STATE_OPEN || state == STATE_PARTIALLY_FILLED || state == STATE_QUEUED;
  }

  private static boolean isTerminal(int state) {
    return state == STATE_FILLED || state == STATE_CANCELED || state == STATE_REJECTED;
  }

  private static boolean contains(long[] values, int count, long value) {
    for (int index = 0; index < count; index++) {
      if (values[index] == value) {
        return true;
      }
    }
    return false;
  }

  private static void requireId(long id, String name) {
    if (id == Long.MIN_VALUE) {
      throw new IllegalArgumentException(name + " is null");
    }
  }

  private static void requireEvent(long sessionId, long eventSequence) {
    requireId(sessionId, "sessionId");
    if (eventSequence < 1) {
      throw new IllegalArgumentException("eventSequence must be positive");
    }
  }
}
