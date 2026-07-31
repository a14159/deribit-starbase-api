package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertNotNull;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.codec.common.UdpPacketHeaderCodec;
import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;

public final class StarbaseMarketDataTransportLifecycleTest {

  public void testApiOwnsBothConfiguredReceiverLoopsUntilExplicitClose() throws Exception {
    InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
    NetworkInterface loopback = NetworkInterface.getByInetAddress(loopbackAddress);
    int incrementalPort = freePort(loopbackAddress);
    int snapshotPort = freePort(loopbackAddress);
    StarbaseMarketDataContext context =
        new StarbaseMarketDataContext(
            ProductGroup.BTC,
            GatewaySide.A,
            loopback.getName(),
            new InetSocketAddress(loopbackAddress, incrementalPort),
            new InetSocketAddress(loopbackAddress, snapshotPort),
            new InetSocketAddress(loopbackAddress, freePort(loopbackAddress)),
            4096,
            4096,
            Duration.ofMillis(250),
            IoPolicy.BLOCKING,
            System::nanoTime);
    StarbaseMarketDataApi api = new StarbaseMarketDataApi(context);

    api.start();
    assertTrue(api.isTransportOpen());
    assertEquals(2, api.receiverLoopCount());

    try (DatagramSocket sender = new DatagramSocket()) {
      sendHeartbeat(sender, context.incrementalGroup(), 44849, 100L);
      sendHeartbeat(sender, context.snapshotGroup(), 44850, 200L);
    }
    awaitPacketCount(api, 2L);
    assertEquals(100L, api.incrementalNextExpectedSequence());
    assertEquals(200L, api.snapshotNextExpectedSequence());
    assertEquals(1L, api.incrementalDiagnostics().packets());
    assertEquals(1L, api.snapshotDiagnostics().packets());

    api.close();
    api.close();

    assertFalse(api.isTransportOpen());
    assertEquals(0, api.receiverLoopCount());
    assertEquals(2L, api.receivedPacketCount());
  }

  public void testCorruptPacketFailsBothReceiverLoopsClosed() throws Exception {
    InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
    NetworkInterface loopback = NetworkInterface.getByInetAddress(loopbackAddress);
    StarbaseMarketDataContext context =
        new StarbaseMarketDataContext(
            ProductGroup.ETH,
            GatewaySide.B,
            loopback.getName(),
            new InetSocketAddress(loopbackAddress, freePort(loopbackAddress)),
            new InetSocketAddress(loopbackAddress, freePort(loopbackAddress)),
            new InetSocketAddress(loopbackAddress, freePort(loopbackAddress)),
            4096,
            4096,
            Duration.ofMillis(250),
            IoPolicy.BLOCKING,
            System::nanoTime);
    StarbaseMarketDataApi api = new StarbaseMarketDataApi(context);
    api.start();

    try (DatagramSocket sender = new DatagramSocket()) {
      byte[] corrupt = {1};
      sender.send(
          new DatagramPacket(corrupt, corrupt.length, context.incrementalGroup()));
    }
    awaitTransportClosed(api);

    assertFalse(api.isTransportOpen());
    assertNotNull(api.transportFailure());
    assertEquals(FeedDiagnostics.UNHEALTHY, api.incrementalDiagnostics().health());
    api.close();
    assertEquals(0, api.receiverLoopCount());
    assertEquals(FeedDiagnostics.CLOSED, api.snapshotDiagnostics().health());
  }

  private static void sendHeartbeat(
      DatagramSocket sender, InetSocketAddress endpoint, int channelId, long sequence)
      throws Exception {
    ByteBuffer buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
    UdpPacketHeaderCodec.encode(
        buffer, 0, 123L, sequence, channelId, UdpPacketHeaderCodec.TYPE_INCREMENTAL_UPDATE, 0);
    sender.send(new DatagramPacket(buffer.array(), buffer.capacity(), endpoint));
  }

  private static int freePort(InetAddress address) throws Exception {
    try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(address, 0))) {
      return socket.getLocalPort();
    }
  }

  private static void awaitPacketCount(StarbaseMarketDataApi api, long expected)
      throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while ((api.receivedPacketCount() < expected
            || api.incrementalDiagnostics().packets()
                    + api.snapshotDiagnostics().packets()
                < expected)
        && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertEquals(expected, api.receivedPacketCount());
  }

  private static void awaitTransportClosed(StarbaseMarketDataApi api)
      throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (api.isTransportOpen() && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertFalse(api.isTransportOpen());
  }
}
