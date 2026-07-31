package io.contek.invoker.deribit.starbase.orderentry.connection;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;

/** Primitive fail-closed inbound/outbound TCP sequence and recovery state. */
public final class SessionSequenceState {

  public static final int ACTION_ACCEPT = 1;
  public static final int ACTION_DUPLICATE = 2;
  public static final int ACTION_RESEND = 3;
  public static final int ACTION_REPLAY = 4;

  private long nextExpectedInbound;
  private long nextOutbound;
  private long lastPeerAcknowledgment;
  private long resendFromSequence;
  private long resendToSequence;
  private long replayFromSequence;
  private long replayToSequence;
  private long nextReplaySequence;
  private boolean failed;

  public SessionSequenceState(long initialInboundSequence, long initialOutboundSequence) {
    requirePositive(initialInboundSequence, "initialInboundSequence");
    requirePositive(initialOutboundSequence, "initialOutboundSequence");
    this.nextExpectedInbound = initialInboundSequence;
    this.nextOutbound = initialOutboundSequence;
  }

  public int onInbound(long sequence, long lastProcessedSequence, boolean resend) {
    requireHealthy();
    validateAcknowledgment(lastProcessedSequence);
    if (sequence < 1) {
      return fail("invalid inbound sequence: " + sequence);
    }
    if (sequence < nextExpectedInbound) {
      return ACTION_DUPLICATE;
    }
    if (sequence > nextExpectedInbound) {
      resendFromSequence = nextExpectedInbound;
      resendToSequence = sequence - 1;
      return ACTION_RESEND;
    }
    if (nextExpectedInbound == Long.MAX_VALUE) {
      return fail("inbound sequence overflow");
    }
    nextExpectedInbound++;
    if (resendToSequence != 0) {
      if (nextExpectedInbound > resendToSequence) {
        resendFromSequence = 0;
        resendToSequence = 0;
      } else {
        resendFromSequence = nextExpectedInbound;
      }
    }
    return ACTION_ACCEPT;
  }

  public int onGapFill(
      long sequence,
      long lastProcessedSequence,
      boolean resend,
      long newSequenceNumber) {
    int action = onInbound(sequence, lastProcessedSequence, resend);
    if (action != ACTION_ACCEPT) {
      return action;
    }
    if (newSequenceNumber < nextExpectedInbound) {
      return fail("gap fill moves sequence backward");
    }
    nextExpectedInbound = newSequenceNumber;
    if (resendToSequence != 0 && nextExpectedInbound > resendToSequence) {
      resendFromSequence = 0;
      resendToSequence = 0;
    }
    return ACTION_ACCEPT;
  }

  public int onPeerResendRequest(
      long sequence,
      long lastProcessedSequence,
      boolean resend,
      long fromSequence,
      long toSequence) {
    int action = onInbound(sequence, lastProcessedSequence, resend);
    if (action != ACTION_ACCEPT) {
      return action;
    }
    long highestSent = nextOutbound - 1;
    long resolvedTo = toSequence == 0 ? highestSent : toSequence;
    if (fromSequence < 1
        || resolvedTo < fromSequence
        || resolvedTo > highestSent) {
      return fail("invalid peer resend range");
    }
    replayFromSequence = fromSequence;
    replayToSequence = resolvedTo;
    nextReplaySequence = fromSequence;
    return ACTION_REPLAY;
  }

  public long claimOutboundSequence() {
    requireHealthy();
    if (nextOutbound == Long.MAX_VALUE) {
      fail("outbound sequence overflow");
    }
    return nextOutbound++;
  }

  public long claimReplaySequence() {
    requireHealthy();
    if (nextReplaySequence == 0 || nextReplaySequence > replayToSequence) {
      throw new IllegalStateException("no outbound replay sequence available");
    }
    long claimed = nextReplaySequence++;
    if (nextReplaySequence > replayToSequence) {
      replayFromSequence = 0;
      replayToSequence = 0;
      nextReplaySequence = 0;
    }
    return claimed;
  }

  public void reset(long inboundSequence, long outboundSequence) {
    requirePositive(inboundSequence, "inboundSequence");
    requirePositive(outboundSequence, "outboundSequence");
    nextExpectedInbound = inboundSequence;
    nextOutbound = outboundSequence;
    lastPeerAcknowledgment = 0;
    resendFromSequence = 0;
    resendToSequence = 0;
    replayFromSequence = 0;
    replayToSequence = 0;
    nextReplaySequence = 0;
    failed = false;
  }

  public long nextExpectedInbound() {
    return nextExpectedInbound;
  }

  public long nextOutbound() {
    return nextOutbound;
  }

  public long lastProcessedInbound() {
    return nextExpectedInbound - 1;
  }

  public long lastPeerAcknowledgment() {
    return lastPeerAcknowledgment;
  }

  public long resendFromSequence() {
    return resendFromSequence;
  }

  public long resendToSequence() {
    return resendToSequence;
  }

  public long replayFromSequence() {
    return replayFromSequence;
  }

  public long replayToSequence() {
    return replayToSequence;
  }

  public boolean isFailed() {
    return failed;
  }

  private void validateAcknowledgment(long acknowledgment) {
    long highestSent = nextOutbound - 1;
    if (acknowledgment < lastPeerAcknowledgment || acknowledgment > highestSent) {
      fail("invalid lastProcessedSequence: " + acknowledgment);
    }
    lastPeerAcknowledgment = acknowledgment;
  }

  private int fail(String message) {
    failed = true;
    throw new StarbaseProtocolException(message);
  }

  private void requireHealthy() {
    if (failed) {
      throw new IllegalStateException("session sequence state has failed");
    }
  }

  private static void requirePositive(long value, String name) {
    if (value < 1) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
