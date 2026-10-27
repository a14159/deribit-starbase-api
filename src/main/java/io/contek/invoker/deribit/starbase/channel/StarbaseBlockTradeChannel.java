package io.contek.invoker.deribit.starbase.channel;

import java.util.Arrays;
import java.util.Objects;

/** Stable block-trade channel with allocation-free dispatch after registration. */
public final class StarbaseBlockTradeChannel {

  private static final StarbaseBlockTradeListener[] EMPTY = new StarbaseBlockTradeListener[0];
  private volatile StarbaseBlockTradeListener[] listeners = EMPTY;

  public synchronized StarbaseSubscription addListener(StarbaseBlockTradeListener listener) {
    Objects.requireNonNull(listener, "listener");
    StarbaseBlockTradeListener[] current = listeners;
    StarbaseBlockTradeListener[] updated = Arrays.copyOf(current, current.length + 1);
    updated[current.length] = listener;
    listeners = updated;
    return new Registration(this, listener);
  }

  public void publish(
      long matchId,
      long instrumentId,
      long blockTradeId,
      long blockRfqId,
      long fillQuantityMantissa,
      long fillPriceMantissa,
      long markPriceMantissa,
      long indexPriceMantissa,
      double impliedVolatility,
      long takerFlags,
      int numberOfLegs,
      int fillIndex,
      boolean lastFill,
      long sequenceNumber,
      long timestampNanos) {
    StarbaseBlockTradeListener[] snapshot = listeners;
    for (int index = 0; index < snapshot.length; index++) {
      snapshot[index].onBlockTrade(
          matchId,
          instrumentId,
          blockTradeId,
          blockRfqId,
          fillQuantityMantissa,
          fillPriceMantissa,
          markPriceMantissa,
          indexPriceMantissa,
          impliedVolatility,
          takerFlags,
          numberOfLegs,
          fillIndex,
          lastFill,
          sequenceNumber,
          timestampNanos);
    }
  }

  public int listenerCount() {
    return listeners.length;
  }

  private synchronized void remove(StarbaseBlockTradeListener listener) {
    StarbaseBlockTradeListener[] current = listeners;
    for (int index = 0; index < current.length; index++) {
      if (current[index] == listener) {
        StarbaseBlockTradeListener[] updated = new StarbaseBlockTradeListener[current.length - 1];
        System.arraycopy(current, 0, updated, 0, index);
        System.arraycopy(current, index + 1, updated, index, current.length - index - 1);
        listeners = updated;
        return;
      }
    }
  }

  private static final class Registration implements StarbaseSubscription {
    private StarbaseBlockTradeChannel channel;
    private final StarbaseBlockTradeListener listener;

    private Registration(StarbaseBlockTradeChannel channel, StarbaseBlockTradeListener listener) {
      this.channel = channel;
      this.listener = listener;
    }

    @Override
    public synchronized void close() {
      if (channel != null) {
        channel.remove(listener);
        channel = null;
      }
    }
  }
}
