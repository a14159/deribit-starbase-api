package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.UdpPacketHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.marketdata.EndOfCycleDecoder;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public final class UdpRetransmitTransportTest {

  public void testExchangesRetransmitDatagramsWithConfiguredLoopbackGateway() throws Exception {
    InetAddress loopback = InetAddress.getLoopbackAddress();
    AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    try (DatagramSocket gateway = new DatagramSocket(new InetSocketAddress(loopback, 0));
        UdpRetransmitTransport transport =
            UdpRetransmitTransport.open(
                new InetSocketAddress(loopback, gateway.getLocalPort()), 4096, 4096)) {
      Thread server =
          Thread.ofPlatform()
              .start(
                  () -> {
                    try {
                      byte[] request = new byte[25];
                      DatagramPacket packet = new DatagramPacket(request, request.length);
                      gateway.receive(packet);
                      byte[] response = successPacket(700L);
                      gateway.send(
                          new DatagramPacket(
                              response, response.length, packet.getSocketAddress()));
                    } catch (Throwable failure) {
                      serverFailure.set(failure);
                    }
                  });
      int[] delivered = {0};
      RetransmitClient client =
          new RetransmitClient(
              transport,
              () -> 1L,
              Duration.ofSeconds(1),
              0,
              1400,
              (buffer, offset, templateId, sequence) -> delivered[0]++);

      assertEquals(RetransmitClient.COMPLETE, client.recover(700L, 1));
      assertEquals(1, delivered[0]);
      assertTrue(transport.isOpen());
      server.join();
      if (serverFailure.get() != null) {
        throw new AssertionError(serverFailure.get());
      }
    }

    UdpRetransmitTransport closed =
        UdpRetransmitTransport.open(
            new InetSocketAddress(loopback, freePort(loopback)), 4096, 4096);
    closed.close();
    closed.close();
    assertFalse(closed.isOpen());
  }

  private static int freePort(InetAddress address) throws Exception {
    try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(address, 0))) {
      return socket.getLocalPort();
    }
  }

  private static byte[] successPacket(long sequence) {
    byte[] bytes = new byte[48];
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    UdpPacketHeaderCodec.encode(
        buffer,
        0,
        1L,
        sequence,
        44849,
        UdpPacketHeaderCodec.TYPE_RETRANSMIT_SUCCESS,
        1);
    int offset = UdpPacketHeaderCodec.ENCODED_LENGTH;
    buffer.putShort(offset, (short) 24);
    buffer.putShort(offset + 2, (short) EndOfCycleDecoder.TEMPLATE_ID);
    buffer.putShort(offset + 4, (short) 1);
    buffer.putInt(offset + MarketDataMessageHeaderCodec.ENCODED_LENGTH, 1);
    return bytes;
  }
}
