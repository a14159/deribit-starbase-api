package io.contek.invoker.deribit.starbase.orderentry.connection;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.orderentry.StarbaseOrderEntryContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;

public final class SocketOrderEntryTransportTest {

  public void testLoopbackTransportOwnsSocketAndPreservesAbsoluteWriteBufferState()
      throws Exception {
    try (ServerSocketChannel server = ServerSocketChannel.open()) {
      server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
      InetSocketAddress endpoint = (InetSocketAddress) server.getLocalAddress();
      StarbaseOrderEntryContext context =
          new StarbaseOrderEntryContext(
              endpoint,
              ProductGroup.BTC,
              GatewaySide.A,
              Duration.ofSeconds(1),
              Duration.ofSeconds(5),
              4096,
              4096,
              IoPolicy.BLOCKING,
              System::nanoTime);
      SocketOrderEntryTransport transport = new SocketOrderEntryTransport(context);
      transport.open();
      try (SocketChannel peer = server.accept()) {
        assertTrue(transport.isOpen());
        ByteBuffer outbound = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN);
        outbound.put(4, (byte) 11);
        outbound.put(5, (byte) 12);
        outbound.put(6, (byte) 13);
        outbound.put(7, (byte) 14);
        outbound.position(2);
        outbound.limit(12);

        assertEquals(4, transport.write(outbound, 4, 4));
        assertEquals(2, outbound.position());
        assertEquals(12, outbound.limit());
        ByteBuffer receivedByPeer = ByteBuffer.allocate(4);
        while (receivedByPeer.hasRemaining()) {
          peer.read(receivedByPeer);
        }
        assertEquals(11, Byte.toUnsignedInt(receivedByPeer.get(0)));
        assertEquals(14, Byte.toUnsignedInt(receivedByPeer.get(3)));

        peer.write(ByteBuffer.wrap(new byte[] {21, 22, 23}));
        ByteBuffer inbound = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(3, transport.read(inbound));
        assertEquals(21, Byte.toUnsignedInt(inbound.get(0)));
        assertEquals(23, Byte.toUnsignedInt(inbound.get(2)));
      } finally {
        transport.close();
      }
      assertFalse(transport.isOpen());
      transport.close();
    }
  }
}
