package io.contek.invoker.deribit.starbase.common;

/** Explicit lifecycle base. Listener count never participates in lifecycle decisions. */
public abstract class AbstractStarbaseApi implements AutoCloseable {

  private static final int NEW = 0;
  private static final int STARTED = 1;
  private static final int CLOSED = 2;

  private volatile int state = NEW;

  public final synchronized void start() {
    if (state == CLOSED) {
      throw new IllegalStateException("API is closed");
    }
    if (state == NEW) {
      onStart();
      state = STARTED;
    }
  }

  public final boolean isStarted() {
    return state == STARTED;
  }

  public final boolean isClosed() {
    return state == CLOSED;
  }

  @Override
  public final synchronized void close() {
    if (state != CLOSED) {
      try {
        onClose();
      } finally {
        state = CLOSED;
      }
    }
  }

  protected void onStart() {}

  protected void onClose() {}
}
