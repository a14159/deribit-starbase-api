package io.contek.invoker.deribit.starbase.marketdata;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;

/** Primitive snapshot/incremental overlap and recovery state machine. */
public final class SnapshotSynchronization {

  public static final int STARTED = 1;
  public static final int RESTARTED = 2;
  public static final int IGNORE = 3;
  public static final int BUFFER = 4;
  public static final int REPLAY_REQUIRED = 5;
  public static final int LIVE = 6;
  public static final int APPLY = 7;
  public static final int RECOVER = 8;
  public static final int RETRY_HELD = 9;
  public static final int FRESH_SNAPSHOT_REQUIRED = 10;

  private static final int WAITING_STATE = 0;
  private static final int SNAPSHOT_STATE = 1;
  private static final int REPLAY_STATE = 2;
  private static final int LIVE_STATE = 3;
  private static final int RECOVERY_STATE = 4;
  private static final int FRESH_SNAPSHOT_STATE = 5;

  private int state;
  private long snapshotAnchor;
  private long bufferedNextSequence;
  private boolean hasBufferedIncremental;
  private boolean snapshotGap;
  private long nextExpectedSequence;
  private long missingBeginSequence;
  private long missingMessageCount;
  private long heldSequence;

  public int beginSnapshot(long incrementalAnchor) {
    requireIncrementableSequence(incrementalAnchor, "incrementalAnchor");
    int result = state == WAITING_STATE ? STARTED : RESTARTED;
    state = SNAPSHOT_STATE;
    snapshotAnchor = incrementalAnchor;
    bufferedNextSequence = incrementalAnchor + 1;
    hasBufferedIncremental = false;
    snapshotGap = false;
    missingBeginSequence = 0;
    missingMessageCount = 0;
    heldSequence = 0;
    return result;
  }

  public int completeSnapshot(long incrementalAnchor) {
    if (state != SNAPSHOT_STATE) {
      throw new StarbaseProtocolException("SnapshotTrailer without active synchronization");
    }
    if (incrementalAnchor != snapshotAnchor) {
      throw new StarbaseProtocolException("snapshot synchronization anchor mismatch");
    }
    if (snapshotGap) {
      state = RECOVERY_STATE;
      return RECOVER;
    }
    if (hasBufferedIncremental) {
      state = REPLAY_STATE;
      nextExpectedSequence = bufferedNextSequence;
      return REPLAY_REQUIRED;
    }
    state = LIVE_STATE;
    nextExpectedSequence = snapshotAnchor + 1;
    return LIVE;
  }

  public int completeReplay(long replayNextSequence) {
    if (state != REPLAY_STATE) {
      throw new StarbaseProtocolException("buffered replay completed outside replay state");
    }
    if (replayNextSequence != nextExpectedSequence) {
      throw new StarbaseProtocolException(
          "buffered replay cursor mismatch: expected="
              + nextExpectedSequence
              + ", actual="
              + replayNextSequence);
    }
    state = LIVE_STATE;
    return LIVE;
  }

  public int onIncremental(long sequence) {
    requireIncrementableSequence(sequence, "sequence");
    if (state == SNAPSHOT_STATE) {
      return onSnapshotIncremental(sequence);
    }
    if (state == LIVE_STATE) {
      if (sequence == nextExpectedSequence) {
        nextExpectedSequence = sequence + 1;
        return APPLY;
      }
      if (sequence < nextExpectedSequence) {
        return IGNORE;
      }
      beginRecovery(nextExpectedSequence, sequence);
      return RECOVER;
    }
    if (state == RECOVERY_STATE || state == REPLAY_STATE) {
      return BUFFER;
    }
    if (state == FRESH_SNAPSHOT_STATE || state == WAITING_STATE) {
      return FRESH_SNAPSHOT_REQUIRED;
    }
    throw new StarbaseProtocolException("unknown snapshot synchronization state");
  }

  public int retransmitComplete(long recoveredNextSequence) {
    if (state != RECOVERY_STATE) {
      throw new StarbaseProtocolException("retransmit completed outside recovery state");
    }
    if (recoveredNextSequence != heldSequence) {
      throw new StarbaseProtocolException(
          "retransmit cursor mismatch: expected="
              + heldSequence
              + ", actual="
              + recoveredNextSequence);
    }
    nextExpectedSequence = recoveredNextSequence;
    missingBeginSequence = 0;
    missingMessageCount = 0;
    state = LIVE_STATE;
    return RETRY_HELD;
  }

  public int retransmitFailed() {
    if (state != RECOVERY_STATE) {
      throw new StarbaseProtocolException("retransmit failed outside recovery state");
    }
    state = FRESH_SNAPSHOT_STATE;
    return FRESH_SNAPSHOT_REQUIRED;
  }

  public boolean isLive() {
    return state == LIVE_STATE;
  }

  public boolean needsFreshSnapshot() {
    return state == FRESH_SNAPSHOT_STATE || state == WAITING_STATE;
  }

  public long nextExpectedSequence() {
    if (state != LIVE_STATE && state != REPLAY_STATE) {
      throw new IllegalStateException("synchronization has no applicable live cursor");
    }
    return nextExpectedSequence;
  }

  public long missingBeginSequence() {
    if (state != RECOVERY_STATE && !snapshotGap) {
      throw new IllegalStateException("synchronization has no unresolved gap");
    }
    return missingBeginSequence;
  }

  public long missingMessageCount() {
    if (state != RECOVERY_STATE && !snapshotGap) {
      throw new IllegalStateException("synchronization has no unresolved gap");
    }
    return missingMessageCount;
  }

  public void reset() {
    state = WAITING_STATE;
    snapshotAnchor = 0;
    bufferedNextSequence = 0;
    hasBufferedIncremental = false;
    snapshotGap = false;
    nextExpectedSequence = 0;
    missingBeginSequence = 0;
    missingMessageCount = 0;
    heldSequence = 0;
  }

  private int onSnapshotIncremental(long sequence) {
    if (sequence <= snapshotAnchor || sequence < bufferedNextSequence) {
      return IGNORE;
    }
    if (sequence == bufferedNextSequence) {
      hasBufferedIncremental = true;
      bufferedNextSequence = sequence + 1;
      return BUFFER;
    }
    if (!snapshotGap) {
      snapshotGap = true;
      missingBeginSequence = bufferedNextSequence;
      missingMessageCount = sequence - bufferedNextSequence;
      heldSequence = sequence;
    }
    return RECOVER;
  }

  private void beginRecovery(long missingBegin, long firstHeldSequence) {
    missingBeginSequence = missingBegin;
    missingMessageCount = firstHeldSequence - missingBegin;
    heldSequence = firstHeldSequence;
    state = RECOVERY_STATE;
  }

  private static void requireIncrementableSequence(long sequence, String name) {
    if (sequence < 0 || sequence == Long.MAX_VALUE) {
      throw new IllegalArgumentException(name + " out of supported range: " + sequence);
    }
  }
}
