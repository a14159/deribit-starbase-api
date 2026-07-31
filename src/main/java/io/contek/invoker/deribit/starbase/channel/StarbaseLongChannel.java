package io.contek.invoker.deribit.starbase.channel;

import java.util.Arrays;
import java.util.Objects;

/** Stable local channel with configuration-time listener allocation and allocation-free dispatch. */
public final class StarbaseLongChannel {

  private static final StarbaseLongListener[] EMPTY = new StarbaseLongListener[0];

  private volatile StarbaseLongListener[] listeners = EMPTY;

  public synchronized StarbaseSubscription addListener(StarbaseLongListener listener) {
    Objects.requireNonNull(listener, "listener");
    StarbaseLongListener[] current = listeners;
    StarbaseLongListener[] updated = Arrays.copyOf(current, current.length + 1);
    updated[current.length] = listener;
    listeners = updated;
    return new Registration(this, listener);
  }

  /** Transport-facing dispatch entry point. Does not allocate. */
  public void publish(long key, long value, long timestampNanos) {
    StarbaseLongListener[] snapshot = listeners;
    for (int index = 0; index < snapshot.length; index++) {
      snapshot[index].onEvent(key, value, timestampNanos);
    }
  }

  public int listenerCount() {
    return listeners.length;
  }

  private synchronized void remove(StarbaseLongListener listener) {
    StarbaseLongListener[] current = listeners;
    for (int index = 0; index < current.length; index++) {
      if (current[index] == listener) {
        StarbaseLongListener[] updated = new StarbaseLongListener[current.length - 1];
        System.arraycopy(current, 0, updated, 0, index);
        System.arraycopy(current, index + 1, updated, index, current.length - index - 1);
        listeners = updated;
        return;
      }
    }
  }

  private static final class Registration implements StarbaseSubscription {

    private StarbaseLongChannel channel;
    private final StarbaseLongListener listener;

    private Registration(StarbaseLongChannel channel, StarbaseLongListener listener) {
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
