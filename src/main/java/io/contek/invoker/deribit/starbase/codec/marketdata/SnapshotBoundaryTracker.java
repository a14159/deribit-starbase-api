package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;

/** Allocation-free structural validation for per-instrument snapshot boundaries. */
public final class SnapshotBoundaryTracker {

  private boolean snapshotOpen;
  private long instrumentId;
  private long incrementalTimestampNanos;
  private long incrementalSequenceNumber;
  private long completedSnapshotCount;

  public void onHeader(
      long instrumentId, long incrementalTimestampNanos, long incrementalSequenceNumber) {
    if (snapshotOpen) {
      throw new StarbaseProtocolException("nested SnapshotHeader");
    }
    this.instrumentId = instrumentId;
    this.incrementalTimestampNanos = incrementalTimestampNanos;
    this.incrementalSequenceNumber = incrementalSequenceNumber;
    snapshotOpen = true;
  }

  public void onTrailer(
      long instrumentId, long incrementalTimestampNanos, long incrementalSequenceNumber) {
    if (!snapshotOpen) {
      throw new StarbaseProtocolException("SnapshotTrailer without SnapshotHeader");
    }
    if (this.instrumentId != instrumentId
        || this.incrementalTimestampNanos != incrementalTimestampNanos
        || this.incrementalSequenceNumber != incrementalSequenceNumber) {
      throw new StarbaseProtocolException("SnapshotTrailer anchor does not match header");
    }
    snapshotOpen = false;
    completedSnapshotCount++;
  }

  public void onEndOfCycle() {
    if (snapshotOpen) {
      throw new StarbaseProtocolException("EndOfCycle inside an open snapshot");
    }
  }

  public boolean isSnapshotOpen() {
    return snapshotOpen;
  }

  public long completedSnapshotCount() {
    return completedSnapshotCount;
  }

  public void reset() {
    snapshotOpen = false;
    instrumentId = 0;
    incrementalTimestampNanos = 0;
    incrementalSequenceNumber = 0;
    completedSnapshotCount = 0;
  }
}
