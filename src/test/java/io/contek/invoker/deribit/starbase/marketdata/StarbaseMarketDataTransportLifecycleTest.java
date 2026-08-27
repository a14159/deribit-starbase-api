package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertNotNull;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.codec.common.UdpPacketHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidPutDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.EndOfCycleDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.SnapshotHeaderDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.SnapshotTrailerDecoder;
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

  public void testRedundantApiOwnsFourReceiverLoopsAndKeepsReadinessClosedBeforeSnapshot()
      throws Exception {
    InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
    NetworkInterface loopback = NetworkInterface.getByInetAddress(loopbackAddress);
    StarbaseMarketDataContext a = context(loopbackAddress, loopback, GatewaySide.A);
    StarbaseMarketDataContext b = context(loopbackAddress, loopback, GatewaySide.B);
    StarbaseMarketDataApi api = new StarbaseMarketDataApi(a, b);

    api.start();
    assertTrue(api.isTransportOpen());
    assertEquals(4, api.receiverLoopCount());
    assertFalse(api.isReady());

    try (DatagramSocket sender = new DatagramSocket()) {
      sendHeartbeat(
          sender,
          a.incrementalGroup(),
          44849,
          100L,
          UdpPacketHeaderCodec.TYPE_INCREMENTAL_UPDATE);
      sendHeartbeat(
          sender,
          a.snapshotGroup(),
          44850,
          200L,
          UdpPacketHeaderCodec.TYPE_SNAPSHOT);
      sendHeartbeat(
          sender,
          b.incrementalGroup(),
          44849,
          100L,
          UdpPacketHeaderCodec.TYPE_INCREMENTAL_UPDATE);
      sendHeartbeat(
          sender,
          b.snapshotGroup(),
          44850,
          200L,
          UdpPacketHeaderCodec.TYPE_SNAPSHOT);
    }
    awaitPacketCount(api, 4L);
    assertEquals(1L, api.incrementalDiagnostics(GatewaySide.A).packets());
    assertEquals(1L, api.snapshotDiagnostics(GatewaySide.A).packets());
    assertEquals(1L, api.incrementalDiagnostics(GatewaySide.B).packets());
    assertEquals(1L, api.snapshotDiagnostics(GatewaySide.B).packets());

    api.close();
    assertFalse(api.isTransportOpen());
    assertEquals(0, api.receiverLoopCount());
  }

  public void testRedundantPublicLifecycleOpensReadinessAfterOneFreshAtomicCycle()
      throws Exception {
    InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
    NetworkInterface loopback = NetworkInterface.getByInetAddress(loopbackAddress);
    StarbaseMarketDataContext a = context(loopbackAddress, loopback, GatewaySide.A);
    StarbaseMarketDataContext b = context(loopbackAddress, loopback, GatewaySide.B);
    StarbaseMarketDataApi api = new StarbaseMarketDataApi(a, b);
    api.instrumentRegistry()
        .upsert(
            100L,
            "BTC-PERPETUAL",
            ProductGroup.BTC,
            "BTC",
            "USD",
            -8,
            1L,
            1L,
            0,
            0,
            1);
    api.configureOrderBook(100L, 8, 8);
    api.start();

    try (DatagramSocket sender = new DatagramSocket()) {
      send(sender, a.incrementalGroup(), endOfCyclePacket(99L, false));
      send(sender, a.snapshotGroup(), endOfCyclePacket(1L, true));
      send(sender, a.snapshotGroup(), snapshotBoundaryPacket(2L, true, 99L));
      send(sender, a.snapshotGroup(), snapshotPutPacket(3L));
      send(sender, a.snapshotGroup(), snapshotBoundaryPacket(4L, false, 99L));
      send(sender, a.snapshotGroup(), endOfCyclePacket(5L, true));
    }
    awaitReady(api);

    assertTrue(api.isReady());
    assertTrue(api.isSynchronized());
    assertTrue(api.isOrderBookReady(100L));
    api.close();
    assertFalse(api.isReady());
  }

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
    sendHeartbeat(
        sender,
        endpoint,
        channelId,
        sequence,
        UdpPacketHeaderCodec.TYPE_INCREMENTAL_UPDATE);
  }

  private static void sendHeartbeat(
      DatagramSocket sender,
      InetSocketAddress endpoint,
      int channelId,
      long sequence,
      int packetType)
      throws Exception {
    ByteBuffer buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
    UdpPacketHeaderCodec.encode(
        buffer, 0, 123L, sequence, channelId, packetType, 0);
    sender.send(new DatagramPacket(buffer.array(), buffer.capacity(), endpoint));
  }

  private static void send(
      DatagramSocket sender, InetSocketAddress endpoint, ByteBuffer buffer)
      throws Exception {
    sender.send(
        new DatagramPacket(buffer.array(), buffer.limit(), endpoint));
  }

  private static ByteBuffer endOfCyclePacket(long sequence, boolean snapshot) {
    ByteBuffer packet =
        packet(
            sequence,
            snapshot
                ? UdpPacketHeaderCodec.TYPE_SNAPSHOT
                : UdpPacketHeaderCodec.TYPE_INCREMENTAL_UPDATE,
            EndOfCycleDecoder.TEMPLATE_ID,
            0,
            EndOfCycleDecoder.BLOCK_LENGTH);
    packet.putInt(
        UdpPacketHeaderCodec.ENCODED_LENGTH + MarketDataMessageHeaderCodec.ENCODED_LENGTH,
        1);
    return packet;
  }

  private static ByteBuffer snapshotBoundaryPacket(
      long sequence, boolean header, long anchor) {
    ByteBuffer packet =
        packet(
            sequence,
            UdpPacketHeaderCodec.TYPE_SNAPSHOT,
            header ? SnapshotHeaderDecoder.TEMPLATE_ID : SnapshotTrailerDecoder.TEMPLATE_ID,
            header
                ? MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION
                : MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION,
            24);
    int body = UdpPacketHeaderCodec.ENCODED_LENGTH + MarketDataMessageHeaderCodec.ENCODED_LENGTH;
    packet.putLong(body, 100L);
    packet.putLong(body + 8, 1_000L);
    packet.putLong(body + 16, anchor);
    return packet;
  }

  private static ByteBuffer snapshotPutPacket(long sequence) {
    ByteBuffer packet =
        packet(
            sequence,
            UdpPacketHeaderCodec.TYPE_SNAPSHOT,
            BidPutDecoder.TEMPLATE_ID,
            MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION
                | MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION,
            BidPutDecoder.BLOCK_LENGTH);
    int body = UdpPacketHeaderCodec.ENCODED_LENGTH + MarketDataMessageHeaderCodec.ENCODED_LENGTH;
    packet.putLong(body, 1L);
    packet.putLong(body + 8, 100L);
    packet.putLong(body + 16, 10L);
    packet.putLong(body + 24, 100L);
    packet.putLong(body + 32, 1L);
    return packet;
  }

  private static ByteBuffer packet(
      long sequence, int packetType, int templateId, int flags, int bodyLength) {
    int messageLength = MarketDataMessageHeaderCodec.ENCODED_LENGTH + bodyLength;
    ByteBuffer packet =
        ByteBuffer.allocate(UdpPacketHeaderCodec.ENCODED_LENGTH + messageLength)
            .order(ByteOrder.LITTLE_ENDIAN);
    UdpPacketHeaderCodec.encode(packet, 0, 1L, sequence, 1, packetType, 1);
    int message = UdpPacketHeaderCodec.ENCODED_LENGTH;
    packet.putShort(message, (short) messageLength);
    packet.putShort(message + 2, (short) templateId);
    packet.putShort(message + 4, (short) 1);
    packet.putShort(message + 6, (short) flags);
    packet.putLong(message + 8, sequence);
    packet.limit(packet.capacity());
    return packet;
  }

  private static StarbaseMarketDataContext context(
      InetAddress address, NetworkInterface networkInterface, GatewaySide side)
      throws Exception {
    return new StarbaseMarketDataContext(
        ProductGroup.BTC,
        side,
        networkInterface.getName(),
        new InetSocketAddress(address, freePort(address)),
        new InetSocketAddress(address, freePort(address)),
        new InetSocketAddress(address, freePort(address)),
        4096,
        4096,
        Duration.ofMillis(25),
        IoPolicy.BLOCKING,
        System::nanoTime);
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

  private static void awaitReady(StarbaseMarketDataApi api) {
    long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (!api.isReady() && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertTrue(api.isReady());
  }
}
