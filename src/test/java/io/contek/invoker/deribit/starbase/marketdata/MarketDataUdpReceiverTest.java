package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertSame;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.common.IoPolicy;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MarketDataUdpReceiverTest {

  public void testReceivesLoopbackDatagramsIntoOneReusableDirectLittleEndianBuffer() throws Exception {
    NetworkInterface loopback = NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress());
    AtomicInteger calls = new AtomicInteger();
    AtomicReference<ByteBuffer> firstBuffer = new AtomicReference<>();
    AtomicInteger lastLength = new AtomicInteger();

    try (MarketDataUdpReceiver receiver =
            MarketDataUdpReceiver.open(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                loopback.getName(),
                4096,
                IoPolicy.BLOCKING,
                (buffer, length) -> {
                  assertTrue(buffer.isDirect());
                  assertSame(ByteOrder.LITTLE_ENDIAN, buffer.order());
                  assertEquals(length, buffer.limit());
                  ByteBuffer previous = firstBuffer.get();
                  if (previous == null) {
                    firstBuffer.set(buffer);
                  } else {
                    assertSame(previous, buffer);
                  }
                  lastLength.set(length);
                  calls.incrementAndGet();
                });
        DatagramSocket sender = new DatagramSocket()) {
      assertTrue(receiver.isOpen());
      byte[] first = {1, 2, 3};
      sender.send(new DatagramPacket(first, first.length, receiver.localAddress()));
      assertEquals(first.length, receiver.receive());
      byte[] second = {4, 5};
      sender.send(new DatagramPacket(second, second.length, receiver.localAddress()));
      assertEquals(second.length, receiver.receive());
      assertEquals(2, calls.get());
      assertEquals(second.length, lastLength.get());
    }
  }

  public void testCloseIsIdempotentAndRejectsUnknownInterfaces() throws Exception {
    NetworkInterface loopback = NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress());
    MarketDataUdpReceiver receiver =
        MarketDataUdpReceiver.open(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            loopback.getName(),
            4096,
            IoPolicy.SPIN,
            (buffer, length) -> {});

    assertEquals(0, receiver.receive());
    receiver.close();
    receiver.close();

    assertFalse(receiver.isOpen());
    io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            MarketDataUdpReceiver.open(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                "definitely-no-such-interface",
                4096,
                IoPolicy.BLOCKING,
                (buffer, length) -> {}));
  }
}
