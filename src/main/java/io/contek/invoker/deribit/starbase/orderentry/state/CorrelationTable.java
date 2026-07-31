package io.contek.invoker.deribit.starbase.orderentry.state;

/** Fixed-capacity primitive correlation slots retained through terminal response consumption. */
public final class CorrelationTable {

  public static final int STATE_EMPTY = 0;
  public static final int STATE_PENDING = 1;
  public static final int STATE_COMPLETED = 2;
  public static final int STATE_TIMED_OUT = 3;

  private final long[] correlationIds;
  private final byte[] states;
  private final int[] commandTypes;
  private final long[] clientOrderIds;
  private final long[] deadlines;
  private final int[] resultCodes;
  private final long[] orderIds;
  private long nextCorrelationId;
  private boolean idExhausted;
  private int size;
  private int expiryCursor;

  public CorrelationTable(int capacity, long initialCorrelationId) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    if (initialCorrelationId < 1) {
      throw new IllegalArgumentException("initialCorrelationId must be positive");
    }
    correlationIds = new long[capacity];
    states = new byte[capacity];
    commandTypes = new int[capacity];
    clientOrderIds = new long[capacity];
    deadlines = new long[capacity];
    resultCodes = new int[capacity];
    orderIds = new long[capacity];
    nextCorrelationId = initialCorrelationId;
  }

  public synchronized long register(
      int commandType, long clientOrderId, long nowNanos, long timeoutNanos) {
    if (commandType < 1) {
      throw new IllegalArgumentException("commandType must be positive");
    }
    if (clientOrderId == Long.MIN_VALUE) {
      throw new IllegalArgumentException("clientOrderId is null");
    }
    if (timeoutNanos < 1) {
      throw new IllegalArgumentException("timeoutNanos must be positive");
    }
    if (idExhausted) {
      throw new IllegalStateException("correlation ID space exhausted");
    }
    int slot = emptySlot();
    if (slot < 0) {
      throw new IllegalStateException("correlation table exhausted");
    }
    long correlationId = nextCorrelationId;
    if (correlationId == Long.MAX_VALUE) {
      idExhausted = true;
    } else {
      nextCorrelationId = correlationId + 1;
    }
    correlationIds[slot] = correlationId;
    states[slot] = STATE_PENDING;
    commandTypes[slot] = commandType;
    clientOrderIds[slot] = clientOrderId;
    deadlines[slot] = saturatingAdd(nowNanos, timeoutNanos);
    resultCodes[slot] = 0;
    orderIds[slot] = Long.MIN_VALUE;
    size++;
    return correlationId;
  }

  public synchronized boolean complete(long correlationId, int resultCode, long orderId) {
    int slot = find(correlationId);
    if (slot < 0 || states[slot] != STATE_PENDING) {
      return false;
    }
    states[slot] = STATE_COMPLETED;
    resultCodes[slot] = resultCode;
    orderIds[slot] = orderId;
    return true;
  }

  /** Marks and returns one expired correlation ID, or zero when none are due. */
  public synchronized long expireNext(long nowNanos) {
    int capacity = states.length;
    for (int checked = 0; checked < capacity; checked++) {
      int slot = expiryCursor++;
      if (expiryCursor == capacity) {
        expiryCursor = 0;
      }
      if (states[slot] == STATE_PENDING && deadlineReached(nowNanos, deadlines[slot])) {
        states[slot] = STATE_TIMED_OUT;
        return correlationIds[slot];
      }
    }
    return 0;
  }

  public synchronized boolean release(long correlationId) {
    int slot = find(correlationId);
    if (slot < 0) {
      return false;
    }
    states[slot] = STATE_EMPTY;
    correlationIds[slot] = 0;
    commandTypes[slot] = 0;
    clientOrderIds[slot] = 0;
    deadlines[slot] = 0;
    resultCodes[slot] = 0;
    orderIds[slot] = 0;
    size--;
    return true;
  }

  public synchronized int state(long correlationId) {
    int slot = find(correlationId);
    return slot < 0 ? STATE_EMPTY : states[slot];
  }

  public synchronized int commandType(long correlationId) {
    return commandTypes[requiredSlot(correlationId)];
  }

  public synchronized long clientOrderId(long correlationId) {
    return clientOrderIds[requiredSlot(correlationId)];
  }

  public synchronized long deadlineNanos(long correlationId) {
    return deadlines[requiredSlot(correlationId)];
  }

  public synchronized int resultCode(long correlationId) {
    return resultCodes[requiredSlot(correlationId)];
  }

  public synchronized long orderId(long correlationId) {
    return orderIds[requiredSlot(correlationId)];
  }

  public synchronized int size() {
    return size;
  }

  public int capacity() {
    return states.length;
  }

  private int emptySlot() {
    for (int slot = 0; slot < states.length; slot++) {
      if (states[slot] == STATE_EMPTY) {
        return slot;
      }
    }
    return -1;
  }

  private int requiredSlot(long correlationId) {
    int slot = find(correlationId);
    if (slot < 0) {
      throw new IllegalArgumentException("unknown correlationId: " + correlationId);
    }
    return slot;
  }

  private int find(long correlationId) {
    if (correlationId < 1) {
      return -1;
    }
    for (int slot = 0; slot < states.length; slot++) {
      if (states[slot] != STATE_EMPTY && correlationIds[slot] == correlationId) {
        return slot;
      }
    }
    return -1;
  }

  private static boolean deadlineReached(long now, long deadline) {
    return now - deadline >= 0;
  }

  private static long saturatingAdd(long value, long increment) {
    if (value > Long.MAX_VALUE - increment) {
      return Long.MAX_VALUE;
    }
    return value + increment;
  }
}
