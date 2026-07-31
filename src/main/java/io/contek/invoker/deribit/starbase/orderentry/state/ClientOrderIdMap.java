package io.contek.invoker.deribit.starbase.orderentry.state;

/** Bounded reversible external-string to Starbase numeric client-order-ID mapping. */
public final class ClientOrderIdMap {

  private final String[] externalIds;
  private final long[] numericIds;
  private long nextNumericId;
  private boolean idExhausted;
  private int size;

  public ClientOrderIdMap(int capacity, long initialNumericId) {
    this(capacity, initialNumericId, false);
  }

  public ClientOrderIdMap(int capacity, long initialNumericId, boolean initiallyExhausted) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    if (initialNumericId < 1) {
      throw new IllegalArgumentException("initialNumericId must be positive");
    }
    if (initiallyExhausted && initialNumericId != Long.MAX_VALUE) {
      throw new IllegalArgumentException("exhausted checkpoint must retain Long.MAX_VALUE");
    }
    externalIds = new String[capacity];
    numericIds = new long[capacity];
    nextNumericId = initialNumericId;
    idExhausted = initiallyExhausted;
  }

  public synchronized long map(String externalId) {
    if (externalId == null || externalId.isEmpty()) {
      throw new IllegalArgumentException("externalId must not be empty");
    }
    int existing = findExternal(externalId);
    if (existing >= 0) {
      return numericIds[existing];
    }
    if (idExhausted) {
      throw new IllegalStateException("client-order-ID space exhausted");
    }
    int slot = emptySlot();
    if (slot < 0) {
      throw new IllegalStateException("client-order-ID mapping capacity exhausted");
    }
    long numericId = nextNumericId;
    if (numericId == Long.MAX_VALUE) {
      idExhausted = true;
    } else {
      nextNumericId = numericId + 1;
    }
    externalIds[slot] = externalId;
    numericIds[slot] = numericId;
    size++;
    return numericId;
  }

  public synchronized String externalId(long numericId) {
    int slot = findNumeric(numericId);
    if (slot < 0) {
      throw new IllegalArgumentException("unknown numeric client order ID: " + numericId);
    }
    return externalIds[slot];
  }

  /** Restores one still-live mapping before accepting new order IDs after restart. */
  public synchronized boolean restore(String externalId, long numericId) {
    validateExternalId(externalId);
    if (numericId < 1
        || numericId > nextNumericId
        || !idExhausted && numericId == nextNumericId) {
      throw new IllegalArgumentException("restored numeric ID is outside persisted history");
    }
    int externalSlot = findExternal(externalId);
    if (externalSlot >= 0) {
      if (numericIds[externalSlot] != numericId) {
        throw new IllegalArgumentException("external ID is already mapped differently");
      }
      return false;
    }
    if (findNumeric(numericId) >= 0) {
      throw new IllegalArgumentException("numeric ID is already mapped differently");
    }
    int slot = emptySlot();
    if (slot < 0) {
      throw new IllegalStateException("client-order-ID mapping capacity exhausted");
    }
    externalIds[slot] = externalId;
    numericIds[slot] = numericId;
    size++;
    return true;
  }

  /** Releases a mapping only after its order can no longer produce lifecycle events. */
  public synchronized boolean release(long numericId) {
    int slot = findNumeric(numericId);
    if (slot < 0) {
      return false;
    }
    externalIds[slot] = null;
    numericIds[slot] = 0;
    size--;
    return true;
  }

  public synchronized long nextNumericId() {
    return nextNumericId;
  }

  public synchronized boolean isIdExhausted() {
    return idExhausted;
  }

  public synchronized int size() {
    return size;
  }

  private int findExternal(String externalId) {
    for (int slot = 0; slot < externalIds.length; slot++) {
      String candidate = externalIds[slot];
      if (candidate != null && candidate.equals(externalId)) {
        return slot;
      }
    }
    return -1;
  }

  private int findNumeric(long numericId) {
    for (int slot = 0; slot < externalIds.length; slot++) {
      if (externalIds[slot] != null && numericIds[slot] == numericId) {
        return slot;
      }
    }
    return -1;
  }

  private int emptySlot() {
    for (int slot = 0; slot < externalIds.length; slot++) {
      if (externalIds[slot] == null) {
        return slot;
      }
    }
    return -1;
  }

  private static void validateExternalId(String externalId) {
    if (externalId == null || externalId.isEmpty()) {
      throw new IllegalArgumentException("externalId must not be empty");
    }
  }
}
