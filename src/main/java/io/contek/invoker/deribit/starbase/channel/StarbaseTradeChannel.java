package io.contek.invoker.deribit.starbase.channel;

import java.util.Arrays;
import java.util.Objects;

/** Stable detailed-trade channel with allocation-free dispatch after registration. */
public final class StarbaseTradeChannel {

  private static final StarbaseTradeListener[] EMPTY = new StarbaseTradeListener[0];
  private volatile StarbaseTradeListener[] listeners = EMPTY;

  public synchronized StarbaseSubscription addListener(StarbaseTradeListener listener) {
    Objects.requireNonNull(listener, "listener");
    StarbaseTradeListener[] current = listeners;
    StarbaseTradeListener[] updated = Arrays.copyOf(current, current.length + 1);
    updated[current.length] = listener;
    listeners = updated;
    return new Registration(this, listener);
  }

  public void publish(
      long matchId,
      long instrumentId,
      long makerOrderId,
      long fillQuantityMantissa,
      long fillPriceMantissa,
      long makerFlags,
      long takerOrderId,
      long totalFilledMantissa,
      long deepestPriceMantissa,
      long markPriceMantissa,
      long indexPriceMantissa,
      long takerFlags,
      int tradeIndex,
      int tradeCount,
      long sequenceNumber,
      long timestampNanos) {
    StarbaseTradeListener[] snapshot = listeners;
    for (int index = 0; index < snapshot.length; index++) {
      snapshot[index].onTrade(
          matchId,
          instrumentId,
          makerOrderId,
          fillQuantityMantissa,
          fillPriceMantissa,
          makerFlags,
          takerOrderId,
          totalFilledMantissa,
          deepestPriceMantissa,
          markPriceMantissa,
          indexPriceMantissa,
          takerFlags,
          tradeIndex,
          tradeCount,
          sequenceNumber,
          timestampNanos);
    }
  }

  public int listenerCount() {
    return listeners.length;
  }

  private synchronized void remove(StarbaseTradeListener listener) {
    StarbaseTradeListener[] current = listeners;
    for (int index = 0; index < current.length; index++) {
      if (current[index] == listener) {
        StarbaseTradeListener[] updated = new StarbaseTradeListener[current.length - 1];
        System.arraycopy(current, 0, updated, 0, index);
        System.arraycopy(current, index + 1, updated, index, current.length - index - 1);
        listeners = updated;
        return;
      }
    }
  }

  private static final class Registration implements StarbaseSubscription {
    private StarbaseTradeChannel channel;
    private final StarbaseTradeListener listener;

    private Registration(StarbaseTradeChannel channel, StarbaseTradeListener listener) {
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
