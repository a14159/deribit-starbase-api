package io.contek.invoker.deribit.starbase.marketdata;

/** Primitive sequence-level A/B earliest-copy arbitrator. */
public final class FeedArbitrator {

  public static final int SOURCE_A = 1;
  public static final int SOURCE_B = 2;

  public static final int ACCEPTED = 1;
  public static final int DUPLICATE = 2;
  public static final int GAP = 3;

  private volatile boolean initialized;
  private volatile long nextExpectedSequence;
  private volatile long gapSize;
  private volatile int lastAcceptedSource;

  /**
   * Atomically arbitrates one message sequence. Calls from independent A/B receiver threads are
   * serialized so exactly one earliest copy can win.
   */
  public synchronized int accept(int source, long sequence) {
    requireSource(source);
    if (sequence < 0 || sequence == Long.MAX_VALUE) {
      throw new IllegalArgumentException("sequence out of supported range: " + sequence);
    }
    if (!initialized) {
      initialized = true;
      nextExpectedSequence = sequence + 1;
      gapSize = 0;
      lastAcceptedSource = source;
      return ACCEPTED;
    }
    if (sequence == nextExpectedSequence) {
      nextExpectedSequence = sequence + 1;
      gapSize = 0;
      lastAcceptedSource = source;
      return ACCEPTED;
    }
    if (sequence < nextExpectedSequence) {
      return DUPLICATE;
    }
    gapSize = sequence - nextExpectedSequence;
    return GAP;
  }

  public long nextExpectedSequence() {
    if (!initialized) {
      throw new IllegalStateException("feed arbitrator is not initialized");
    }
    return nextExpectedSequence;
  }

  public boolean isInitialized() {
    return initialized;
  }

  public long gapSize() {
    return gapSize;
  }

  public int lastAcceptedSource() {
    if (!initialized) {
      throw new IllegalStateException("feed arbitrator is not initialized");
    }
    return lastAcceptedSource;
  }

  public synchronized void reset() {
    initialized = false;
    nextExpectedSequence = 0;
    gapSize = 0;
    lastAcceptedSource = 0;
  }

  private static void requireSource(int source) {
    if (source != SOURCE_A && source != SOURCE_B) {
      throw new IllegalArgumentException("unknown feed source: " + source);
    }
  }
}
