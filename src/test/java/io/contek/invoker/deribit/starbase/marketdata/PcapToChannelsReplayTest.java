package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;

import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.codec.common.UdpPacketHeaderCodec;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;

public final class PcapToChannelsReplayTest {

  private static final int REPLAY_CHANNEL_ID = 44_853;
  private static final long FIRST_INSTRUMENT_ID = 127_786L;

  public void testOfficialPcapProducesDeterministicStatefulChannelGolden() throws IOException {
    ReplayResult first = replay();
    ReplayResult second = replay();

    assertEquals(first, second);
    assertEquals(
        new ReplayResult(2_115L, 861L, 7_987_323_303_025_136_854L, 0L, true),
        first);
  }

  private static ReplayResult replay() throws IOException {
    StarbaseMarketDataApi api = api();
    long[] values = new long[4];
    api.getReferenceDataChannel()
        .addListener((key, value, timestamp) -> values[0]++);
    api.getOrderBookChannel(FIRST_INSTRUMENT_ID)
        .addListener(
            (price, quantity, timestamp) -> {
              values[1]++;
              values[2] =
                  values[2] * 31
                      + price
                      + Long.rotateLeft(quantity, 17)
                      + Long.rotateLeft(timestamp, 33);
            });
    api.getTradesChannel(FIRST_INSTRUMENT_ID)
        .addListener(
            (matchId,
                instrumentId,
                makerOrderId,
                fillQuantity,
                fillPrice,
                makerFlags,
                takerOrderId,
                totalFilled,
                deepestPrice,
                markPrice,
                indexPrice,
                takerFlags,
                tradeIndex,
                tradeCount,
                sequence,
                timestamp) -> values[3]++);

    byte[] pcap = resourceBytes("/pcap/starbase-market-data.pcap");
    ByteBuffer datagram = ByteBuffer.allocateDirect(65_535).order(ByteOrder.LITTLE_ENDIAN);
    int recordOffset = 24;
    while (recordOffset < pcap.length) {
      int capturedLength = int32(pcap, recordOffset + 8);
      int frameOffset = recordOffset + 16;
      int ipOffset = frameOffset + 14;
      int ipHeaderLength = (pcap[ipOffset] & 0x0F) * 4;
      int udpOffset = ipOffset + ipHeaderLength;
      int payloadOffset = udpOffset + 8;
      int payloadLength = networkUInt16(pcap, udpOffset + 4) - 8;
      datagram.clear();
      datagram.put(pcap, payloadOffset, payloadLength);
      datagram.flip();
      if (UdpPacketHeaderCodec.channelId(datagram, 0) == REPLAY_CHANNEL_ID) {
        api.dispatchReplayPacket(datagram);
      }
      recordOffset = frameOffset + capturedLength;
    }
    return new ReplayResult(
        values[0],
        values[1],
        values[2],
        values[3],
        api.isOrderBookReady(FIRST_INSTRUMENT_ID));
  }

  private static StarbaseMarketDataApi api() {
    return new StarbaseMarketDataApi(
        new StarbaseMarketDataContext(
            ProductGroup.BTC,
            GatewaySide.A,
            "loopback",
            new InetSocketAddress("239.1.1.1", 4220),
            new InetSocketAddress("239.1.1.2", 4230),
            new InetSocketAddress("127.0.0.1", 4240),
            4096,
            4096,
            Duration.ofMillis(250),
            IoPolicy.BLOCKING,
            () -> 1L),
        16_384);
  }

  private static byte[] resourceBytes(String name) throws IOException {
    try (InputStream input = PcapToChannelsReplayTest.class.getResourceAsStream(name)) {
      if (input == null) {
        throw new IOException("missing test resource " + name);
      }
      return input.readAllBytes();
    }
  }

  private static int int32(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFF)
        | ((bytes[offset + 1] & 0xFF) << 8)
        | ((bytes[offset + 2] & 0xFF) << 16)
        | ((bytes[offset + 3] & 0xFF) << 24);
  }

  private static int networkUInt16(byte[] bytes, int offset) {
    return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
  }

  private record ReplayResult(
      long referenceEvents,
      long bookEvents,
      long bookHash,
      long tradeEvents,
      boolean ready) {}
}
