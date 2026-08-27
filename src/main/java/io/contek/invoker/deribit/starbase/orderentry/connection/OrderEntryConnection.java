package io.contek.invoker.deribit.starbase.orderentry.connection;

import io.contek.invoker.deribit.starbase.codec.orderentry.OrderEntryMessageHandler;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/** Explicitly started connection owning one transport, read loop, assembler, and writer. */
public final class OrderEntryConnection implements AutoCloseable {

  private static final int NEW = 0;
  private static final int RUNNING = 1;
  private static final int CLOSED = 2;
  private static final int FAILED = 3;

  private final OrderEntryDuplexTransport transport;
  private final ByteBuffer receiveBuffer;
  private final TcpFrameAssembler assembler;
  private final TcpFrameWriter writer;
  private volatile int state = NEW;
  private volatile Throwable failure;
  private Thread eventLoop;
  private boolean transportClosed;

  public OrderEntryConnection(
      OrderEntryDuplexTransport transport,
      int receiveBufferBytes,
      int sendBufferBytes,
      OrderEntryMessageHandler handler) {
    if (receiveBufferBytes < 32) {
      throw new IllegalArgumentException("receiveBufferBytes must hold a TCP header");
    }
    this.transport = Objects.requireNonNull(transport, "transport");
    this.receiveBuffer =
        ByteBuffer.allocateDirect(receiveBufferBytes).order(ByteOrder.LITTLE_ENDIAN);
    this.assembler = new TcpFrameAssembler(receiveBufferBytes, handler);
    this.writer = new TcpFrameWriter(sendBufferBytes, transport::write);
  }

  public synchronized void start() {
    if (state == RUNNING) {
      return;
    }
    if (state != NEW) {
      throw new IllegalStateException("connection cannot start from state " + state);
    }
    try {
      transport.open();
    } catch (RuntimeException openFailure) {
      state = FAILED;
      failure = openFailure;
      closeTransportAfterFailure(openFailure);
      throw openFailure;
    }
    state = RUNNING;
    eventLoop = new Thread(this::runEventLoop, "starbase-order-entry");
    eventLoop.setDaemon(true);
    eventLoop.start();
  }

  public boolean write(TcpFrameEncoder encoder) {
    requireRunning();
    return writer.write(encoder);
  }

  public boolean flush() {
    requireRunning();
    return writer.flush();
  }

  /** Returns the connection-owned serialized writer for composed session components. */
  public TcpFrameWriter writer() {
    return writer;
  }

  public boolean isRunning() {
    return state == RUNNING;
  }

  public boolean isClosed() {
    return state == CLOSED;
  }

  public boolean isFailed() {
    return state == FAILED;
  }

  public Throwable failure() {
    return failure;
  }

  @Override
  public void close() {
    Thread thread;
    synchronized (this) {
      if (state == CLOSED) {
        return;
      }
      state = CLOSED;
      closeTransport();
      thread = eventLoop;
    }
    if (thread != null && thread != Thread.currentThread()) {
      try {
        thread.join(5_000);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void runEventLoop() {
    try {
      while (state == RUNNING) {
        receiveBuffer.clear();
        int read = transport.read(receiveBuffer);
        if (read < -1 || read > receiveBuffer.capacity()) {
          throw new StarbaseProtocolException("invalid TCP read result: " + read);
        }
        if (read == -1) {
          assembler.endOfInput();
          if (state == RUNNING) {
            fail(new StarbaseProtocolException("unexpected TCP EOF"));
          }
          return;
        }
        if (read > 0) {
          assembler.accept(receiveBuffer, 0, read);
        }
      }
    } catch (RuntimeException readFailure) {
      if (state == RUNNING) {
        fail(readFailure);
      }
    }
  }

  private void fail(RuntimeException cause) {
    synchronized (this) {
      if (state != RUNNING) {
        return;
      }
      failure = cause;
      state = FAILED;
      closeTransportAfterFailure(cause);
    }
  }

  private void closeTransportAfterFailure(RuntimeException cause) {
    try {
      closeTransport();
    } catch (RuntimeException closeFailure) {
      cause.addSuppressed(closeFailure);
    }
  }

  private void closeTransport() {
    if (!transportClosed) {
      transportClosed = true;
      transport.close();
    }
  }

  private void requireRunning() {
    if (state != RUNNING) {
      throw new IllegalStateException("order-entry connection is not running");
    }
  }
}
