package io.contek.invoker.deribit.starbase.marketdata;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Connected UDP retransmit gateway transport with a reusable selector. */
public final class UdpRetransmitTransport implements RetransmitTransport, AutoCloseable {

  private final DatagramChannel channel;
  private final Selector selector;

  private UdpRetransmitTransport(DatagramChannel channel, Selector selector) {
    this.channel = channel;
    this.selector = selector;
  }

  public static UdpRetransmitTransport open(
      InetSocketAddress endpoint, int sendBufferBytes, int receiveBufferBytes)
      throws IOException {
    Objects.requireNonNull(endpoint, "endpoint");
    if (endpoint.isUnresolved()) {
      throw new IllegalArgumentException("retransmit endpoint must be resolved");
    }
    if (sendBufferBytes < 24 || receiveBufferBytes < 24) {
      throw new IllegalArgumentException("UDP buffers must be at least 24 bytes");
    }
    DatagramChannel channel = DatagramChannel.open(StandardProtocolFamily.INET);
    Selector selector = null;
    try {
      channel.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferBytes);
      channel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferBytes);
      channel.bind(null);
      channel.connect(endpoint);
      channel.configureBlocking(false);
      selector = Selector.open();
      channel.register(selector, SelectionKey.OP_READ);
      return new UdpRetransmitTransport(channel, selector);
    } catch (IOException | RuntimeException | Error failure) {
      if (selector != null) {
        selector.close();
      }
      channel.close();
      throw failure;
    }
  }

  @Override
  public void send(ByteBuffer request, int length) throws IOException {
    if (length < 1 || length != request.remaining()) {
      throw new IllegalArgumentException("length must equal request.remaining()");
    }
    int written = channel.write(request);
    if (written != length) {
      throw new IOException(
          "UDP retransmit request was not sent atomically: expected="
              + length
              + ", written="
              + written);
    }
  }

  @Override
  public int receive(ByteBuffer response, long timeoutNanos) throws IOException {
    if (timeoutNanos <= 0) {
      throw new IllegalArgumentException("timeoutNanos must be positive");
    }
    long deadline;
    try {
      deadline = Math.addExact(System.nanoTime(), timeoutNanos);
    } catch (ArithmeticException exception) {
      deadline = Long.MAX_VALUE;
    }
    while (true) {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) {
        return 0;
      }
      long timeoutMillis =
          Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining) + 1L);
      if (selector.select(timeoutMillis) == 0) {
        continue;
      }
      selector.selectedKeys().clear();
      int length = channel.read(response);
      if (length > 0) {
        return length;
      }
    }
  }

  public boolean isOpen() {
    return channel.isOpen() && selector.isOpen();
  }

  @Override
  public void close() throws IOException {
    IOException failure = null;
    try {
      selector.close();
    } catch (IOException exception) {
      failure = exception;
    }
    try {
      channel.close();
    } catch (IOException exception) {
      if (failure == null) {
        failure = exception;
      } else {
        failure.addSuppressed(exception);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
