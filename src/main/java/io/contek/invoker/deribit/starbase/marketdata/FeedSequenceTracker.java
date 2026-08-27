package io.contek.invoker.deribit.starbase.marketdata;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;

/** Primitive per-feed UDP sequence state. Not thread-safe; owned by one receiver loop. */
public final class FeedSequenceTracker {

  public static final int INITIALIZED = 1;
  public static final int CONTIGUOUS = 2;
  public static final int HEARTBEAT = 3;
  public static final int DUPLICATE = 4;
  public static final int GAP = 5;

  private volatile boolean initialized;
  private volatile long nextExpectedSequence;
  private volatile long gapSize;

  public int accept(long packetSequence, int messageCount) {
    if (packetSequence < 0) {
      throw new IllegalArgumentException("packetSequence must be non-negative");
    }
    if ((messageCount & ~0xFFFF) != 0) {
      throw new IllegalArgumentException("messageCount out of uint16 range: " + messageCount);
    }
    long packetEnd;
    try {
      packetEnd = Math.addExact(packetSequence, messageCount);
    } catch (ArithmeticException exception) {
      throw new StarbaseProtocolException("UDP sequence range overflow", exception);
    }

    if (!initialized) {
      initialized = true;
      nextExpectedSequence = packetEnd;
      gapSize = 0;
      return messageCount == 0 ? HEARTBEAT : INITIALIZED;
    }
    if (packetSequence == nextExpectedSequence) {
      nextExpectedSequence = packetEnd;
      gapSize = 0;
      return messageCount == 0 ? HEARTBEAT : CONTIGUOUS;
    }
    if (packetSequence > nextExpectedSequence) {
      gapSize = packetSequence - nextExpectedSequence;
      return GAP;
    }
    if (packetEnd <= nextExpectedSequence) {
      return DUPLICATE;
    }
    throw new StarbaseProtocolException(
        "UDP packet partially overlaps expected sequence: packetSequence="
            + packetSequence
            + ", packetEnd="
            + packetEnd
            + ", nextExpected="
            + nextExpectedSequence);
  }

  public boolean isInitialized() {
    return initialized;
  }

  public long nextExpectedSequence() {
    if (!initialized) {
      throw new IllegalStateException("sequence tracker is not initialized");
    }
    return nextExpectedSequence;
  }

  public long gapSize() {
    return gapSize;
  }

  /** Advances past the exact held packet after its preceding gap was recovered or abandoned. */
  public void advanceAfterGap(long packetSequence, int messageCount) {
    if (!initialized || gapSize == 0) {
      throw new IllegalStateException("feed tracker has no open gap");
    }
    if (packetSequence < 0 || (messageCount & ~0xFFFF) != 0) {
      throw new IllegalArgumentException("invalid recovered packet range");
    }
    if (packetSequence != nextExpectedSequence + gapSize) {
      throw new IllegalArgumentException("recovered packet does not match the held gap");
    }
    try {
      nextExpectedSequence = Math.addExact(packetSequence, messageCount);
    } catch (ArithmeticException exception) {
      throw new StarbaseProtocolException("UDP sequence range overflow", exception);
    }
    gapSize = 0;
  }

  public void reset() {
    initialized = false;
    nextExpectedSequence = 0;
    gapSize = 0;
  }
}
