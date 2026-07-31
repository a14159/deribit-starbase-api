package io.contek.invoker.deribit.starbase.marketdata;

import io.contek.invoker.deribit.starbase.common.IoPolicy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.Objects;
import java.util.Enumeration;

/** Owns one configured UDP feed socket and one reusable wire buffer. */
public final class MarketDataUdpReceiver implements AutoCloseable {

  @FunctionalInterface
  public interface PacketHandler {
    void onPacket(ByteBuffer buffer, int length);
  }

  private final DatagramChannel channel;
  private final ByteBuffer receiveBuffer;
  private final PacketHandler handler;
  private final MembershipKey membership;

  private MarketDataUdpReceiver(
      DatagramChannel channel,
      ByteBuffer receiveBuffer,
      PacketHandler handler,
      MembershipKey membership) {
    this.channel = channel;
    this.receiveBuffer = receiveBuffer;
    this.handler = handler;
    this.membership = membership;
  }

  public static MarketDataUdpReceiver open(
      InetSocketAddress endpoint,
      String networkInterfaceName,
      int receiveBufferBytes,
      IoPolicy ioPolicy,
      PacketHandler handler)
      throws IOException {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(networkInterfaceName, "networkInterfaceName");
    Objects.requireNonNull(ioPolicy, "ioPolicy");
    Objects.requireNonNull(handler, "handler");
    if (receiveBufferBytes < 24) {
      throw new IllegalArgumentException("receiveBufferBytes must be at least 24");
    }
    InetAddress address = endpoint.getAddress();
    if (address == null) {
      throw new IllegalArgumentException("endpoint must be resolved");
    }
    NetworkInterface networkInterface = findInterface(networkInterfaceName);
    DatagramChannel channel = DatagramChannel.open(StandardProtocolFamily.INET);
    MembershipKey membership = null;
    try {
      channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
      channel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferBytes);
      channel.configureBlocking(ioPolicy == IoPolicy.BLOCKING);
      if (address.isMulticastAddress()) {
        channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, networkInterface);
        channel.bind(new InetSocketAddress(endpoint.getPort()));
        membership = channel.join(address, networkInterface);
      } else {
        channel.bind(endpoint);
      }
      return new MarketDataUdpReceiver(
          channel,
          ByteBuffer.allocateDirect(receiveBufferBytes).order(ByteOrder.LITTLE_ENDIAN),
          handler,
          membership);
    } catch (IOException | RuntimeException | Error failure) {
      channel.close();
      throw failure;
    }
  }

  public int receive() throws IOException {
    receiveBuffer.clear();
    SocketAddress source = channel.receive(receiveBuffer);
    if (source == null) {
      receiveBuffer.limit(0);
      return 0;
    }
    int length = receiveBuffer.position();
    receiveBuffer.flip();
    handler.onPacket(receiveBuffer, length);
    return length;
  }

  public InetSocketAddress localAddress() throws IOException {
    return (InetSocketAddress) channel.getLocalAddress();
  }

  public boolean isOpen() {
    return channel.isOpen();
  }

  @Override
  public void close() throws IOException {
    if (membership != null) {
      membership.drop();
    }
    channel.close();
  }

  private static NetworkInterface findInterface(String name) throws SocketException {
    NetworkInterface networkInterface = NetworkInterface.getByName(name);
    if (networkInterface == null && "loopback".equalsIgnoreCase(name)) {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface candidate = interfaces.nextElement();
        if (candidate.isLoopback()) {
          networkInterface = candidate;
          break;
        }
      }
    }
    if (networkInterface == null) {
      throw new IllegalArgumentException("unknown network interface: " + name);
    }
    return networkInterface;
  }
}
