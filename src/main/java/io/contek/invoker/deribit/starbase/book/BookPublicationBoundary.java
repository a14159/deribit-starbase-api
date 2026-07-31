package io.contek.invoker.deribit.starbase.book;

import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.util.Objects;

/** Fail-closed transaction/end-of-cycle gate for coherent book notifications. */
public final class BookPublicationBoundary {

  private static final int START =
      MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION;
  private static final int END =
      MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION;
  private static final int KNOWN_FLAGS = START | END;

  private final BookPublicationListener listener;
  private boolean transactionOpen;
  private boolean dirty;
  private boolean failed;
  private long publishedVersion;

  public BookPublicationBoundary(BookPublicationListener listener) {
    this.listener = Objects.requireNonNull(listener, "listener");
  }

  public void onMutation() {
    requireHealthy();
    dirty = true;
  }

  /**
   * Applies the transaction flags of one already-validated and applied message.
   *
   * <p>An end-of-cycle marker is a publication boundary only outside a transaction.
   */
  public void onMessageBoundary(
      int flags, long sequenceNumber, long transactTimeNanos, boolean endOfCycle) {
    requireHealthy();
    if ((flags & ~KNOWN_FLAGS) != 0) {
      fail("unsupported book transaction flags: " + flags);
    }
    if (sequenceNumber < 0 || transactTimeNanos < 0) {
      fail("negative book publication sequence or timestamp");
    }
    boolean starts = (flags & START) != 0;
    boolean ends = (flags & END) != 0;
    if (starts) {
      if (transactionOpen) {
        fail("nested book transaction");
      }
      transactionOpen = true;
    }
    if (endOfCycle && transactionOpen && !ends) {
      fail("EndOfCycle inside an open book transaction");
    }
    if (ends) {
      if (!transactionOpen) {
        fail("book transaction end without start");
      }
      transactionOpen = false;
      publishIfDirty(sequenceNumber, transactTimeNanos);
      return;
    }
    if (endOfCycle) {
      publishIfDirty(sequenceNumber, transactTimeNanos);
    }
  }

  public boolean isTransactionOpen() {
    return transactionOpen;
  }

  public boolean isFailed() {
    return failed;
  }

  public long publishedVersion() {
    return publishedVersion;
  }

  public void reset() {
    transactionOpen = false;
    dirty = false;
    failed = false;
    publishedVersion = 0;
  }

  private void publishIfDirty(long sequenceNumber, long transactTimeNanos) {
    if (!dirty) {
      return;
    }
    long version;
    try {
      version = Math.incrementExact(publishedVersion);
    } catch (ArithmeticException exception) {
      failed = true;
      throw new StarbaseProtocolException("book publication version overflow", exception);
    }
    try {
      listener.onPublication(version, sequenceNumber, transactTimeNanos);
    } catch (RuntimeException exception) {
      failed = true;
      throw exception;
    }
    publishedVersion = version;
    dirty = false;
  }

  private void requireHealthy() {
    if (failed) {
      throw new StarbaseProtocolException("book publication boundary is failed");
    }
  }

  private void fail(String message) {
    failed = true;
    throw new StarbaseProtocolException(message);
  }
}
