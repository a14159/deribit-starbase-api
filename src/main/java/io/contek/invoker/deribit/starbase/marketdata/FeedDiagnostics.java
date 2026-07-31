package io.contek.invoker.deribit.starbase.marketdata;

/** Fixed-size feed health state and saturating diagnostics counters. */
public final class FeedDiagnostics {

  public static final int STARTING = 1;
  public static final int SYNCHRONIZING = 2;
  public static final int LIVE = 3;
  public static final int RECOVERING = 4;
  public static final int UNHEALTHY = 5;
  public static final int CLOSED = 6;

  private volatile boolean transportOpen;
  private volatile boolean closed;
  private volatile boolean fatal;
  private volatile boolean snapshotComplete;
  private volatile boolean gapOpen;
  private volatile boolean transactionOpen;

  private volatile long packets;
  private volatile long messages;
  private volatile long duplicates;
  private volatile long gaps;
  private volatile long retransmitRequests;
  private volatile long retransmitRejects;
  private volatile long snapshotResets;
  private volatile long corruptFrames;
  private volatile long unknownTemplates;
  private volatile long reconnects;
  private volatile long callbackFailures;
  private volatile long bufferExhaustions;

  public int health() {
    if (closed) {
      return CLOSED;
    }
    if (fatal) {
      return UNHEALTHY;
    }
    if (!transportOpen) {
      return STARTING;
    }
    if (gapOpen) {
      return RECOVERING;
    }
    if (!snapshotComplete || transactionOpen) {
      return SYNCHRONIZING;
    }
    return LIVE;
  }

  public boolean isReady() {
    return health() == LIVE;
  }

  public void onTransportOpen() {
    closed = false;
    transportOpen = true;
  }

  public void onTransportClosed() {
    transportOpen = false;
    closed = true;
  }

  public void onSnapshotComplete() {
    snapshotComplete = true;
    gapOpen = false;
  }

  public void onSnapshotReset() {
    snapshotResets = increment(snapshotResets);
    snapshotComplete = false;
    gapOpen = false;
    transactionOpen = false;
  }

  public void onTransactionStart() {
    transactionOpen = true;
  }

  public void onTransactionEnd() {
    transactionOpen = false;
  }

  public void onGap() {
    gaps = increment(gaps);
    gapOpen = true;
  }

  public void onGapRecovered() {
    gapOpen = false;
  }

  public void onCorruptFrame() {
    corruptFrames = increment(corruptFrames);
    fatal = true;
  }

  public void onUnknownTemplate() {
    unknownTemplates = increment(unknownTemplates);
    fatal = true;
  }

  public void onBufferExhaustion() {
    bufferExhaustions = increment(bufferExhaustions);
    fatal = true;
  }

  public void onPacket(int messageCount) {
    if (messageCount < 0) {
      throw new IllegalArgumentException("messageCount must be non-negative");
    }
    packets = increment(packets);
    messages = add(messages, messageCount);
  }

  public void addPackets(long packetCount, long messageCount) {
    if (packetCount < 0 || messageCount < 0) {
      throw new IllegalArgumentException("counter deltas must be non-negative");
    }
    packets = add(packets, packetCount);
    messages = add(messages, messageCount);
  }

  public void onDuplicate() {
    duplicates = increment(duplicates);
  }

  public void onRetransmitRequest() {
    retransmitRequests = increment(retransmitRequests);
  }

  public void onRetransmitReject() {
    retransmitRejects = increment(retransmitRejects);
  }

  public void onReconnect() {
    reconnects = increment(reconnects);
    transportOpen = true;
    closed = false;
    fatal = false;
    snapshotComplete = false;
    gapOpen = false;
    transactionOpen = false;
  }

  public void onCallbackFailure() {
    callbackFailures = increment(callbackFailures);
  }

  public void resetCounters() {
    packets = 0;
    messages = 0;
    duplicates = 0;
    gaps = 0;
    retransmitRequests = 0;
    retransmitRejects = 0;
    snapshotResets = 0;
    corruptFrames = 0;
    unknownTemplates = 0;
    reconnects = 0;
    callbackFailures = 0;
    bufferExhaustions = 0;
  }

  public long packets() {
    return packets;
  }

  public long messages() {
    return messages;
  }

  public long duplicates() {
    return duplicates;
  }

  public long gaps() {
    return gaps;
  }

  public long retransmitRequests() {
    return retransmitRequests;
  }

  public long retransmitRejects() {
    return retransmitRejects;
  }

  public long snapshotResets() {
    return snapshotResets;
  }

  public long corruptFrames() {
    return corruptFrames;
  }

  public long unknownTemplates() {
    return unknownTemplates;
  }

  public long reconnects() {
    return reconnects;
  }

  public long callbackFailures() {
    return callbackFailures;
  }

  public long bufferExhaustions() {
    return bufferExhaustions;
  }

  private static long increment(long value) {
    return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1;
  }

  private static long add(long value, long delta) {
    return delta > Long.MAX_VALUE - value ? Long.MAX_VALUE : value + delta;
  }
}
