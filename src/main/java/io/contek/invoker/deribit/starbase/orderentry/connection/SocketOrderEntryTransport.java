package io.contek.invoker.deribit.starbase.orderentry.connection;

import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.StarbaseException;
import io.contek.invoker.deribit.starbase.orderentry.StarbaseOrderEntryContext;
import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Objects;

/** One configured TCP socket owned by one order-entry connection attempt. */
public final class SocketOrderEntryTransport implements OrderEntryDuplexTransport {

  private final StarbaseOrderEntryContext context;
  private SocketChannel channel;

  public SocketOrderEntryTransport(StarbaseOrderEntryContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  public synchronized void open() {
    if (channel != null) {
      throw new IllegalStateException("order-entry transport is already open");
    }
    SocketChannel opening = null;
    try {
      opening = SocketChannel.open();
      opening.setOption(StandardSocketOptions.TCP_NODELAY, true);
      opening.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
      opening.setOption(StandardSocketOptions.SO_RCVBUF, context.receiveBufferBytes());
      opening.setOption(StandardSocketOptions.SO_SNDBUF, context.sendBufferBytes());
      long timeoutMillis = context.connectTimeout().toMillis();
      int boundedTimeout =
          (int) Math.max(1L, Math.min((long) Integer.MAX_VALUE, timeoutMillis));
      opening.socket().connect(context.endpoint(), boundedTimeout);
      opening.configureBlocking(context.ioPolicy() == IoPolicy.BLOCKING);
      channel = opening;
    } catch (IOException | RuntimeException failure) {
      if (opening != null) {
        try {
          opening.close();
        } catch (IOException closeFailure) {
          failure.addSuppressed(closeFailure);
        }
      }
      throw transportFailure("open", failure);
    }
  }

  @Override
  public int read(ByteBuffer buffer) {
    Objects.requireNonNull(buffer, "buffer");
    try {
      return requiredChannel().read(buffer);
    } catch (IOException failure) {
      throw transportFailure("read", failure);
    }
  }

  @Override
  public int write(ByteBuffer buffer, int offset, int length) {
    Objects.requireNonNull(buffer, "buffer");
    if (offset < 0 || length < 0 || offset > buffer.capacity() - length) {
      throw new IndexOutOfBoundsException("invalid TCP write range");
    }
    int originalPosition = buffer.position();
    int originalLimit = buffer.limit();
    try {
      buffer.limit(offset + length);
      buffer.position(offset);
      return requiredChannel().write(buffer);
    } catch (IOException failure) {
      throw transportFailure("write", failure);
    } finally {
      buffer.limit(originalLimit);
      buffer.position(originalPosition);
    }
  }

  public synchronized boolean isOpen() {
    return channel != null && channel.isOpen();
  }

  @Override
  public synchronized void close() {
    if (channel == null) {
      return;
    }
    try {
      channel.close();
    } catch (IOException failure) {
      throw transportFailure("close", failure);
    } finally {
      channel = null;
    }
  }

  private synchronized SocketChannel requiredChannel() {
    if (channel == null || !channel.isOpen()) {
      throw new IllegalStateException("order-entry transport is not open");
    }
    return channel;
  }

  private static StarbaseException transportFailure(String operation, Exception failure) {
    return failure instanceof StarbaseException starbase
        ? starbase
        : new StarbaseException("Starbase order-entry TCP " + operation + " failed", failure);
  }
}
