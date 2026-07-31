package io.contek.invoker.deribit.starbase.codec.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;

import io.contek.invoker.deribit.starbase.codec.common.UdpPacketHeaderCodec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class OfficialPcapReplayTest {

  private static final String PCAP = "/pcap/starbase-market-data.pcap";
  private static final String TRACE = "/pcap/starbase-market-data.trace";

  public void testOfficialCaptureMatchesCheckedInGoldenTraceThroughHardcodedDispatcher()
      throws IOException, NoSuchAlgorithmException {
    byte[] pcap = resourceBytes(PCAP);
    ReplayAccumulator accumulator = new ReplayAccumulator();
    MarketDataPacketDispatcher dispatcher = new MarketDataPacketDispatcher(accumulator);
    ByteBuffer datagram = ByteBuffer.allocateDirect(65_535).order(ByteOrder.LITTLE_ENDIAN);

    assertEquals(0xA1B2C3D4, int32(pcap, 0));
    int recordOffset = 24;
    while (recordOffset < pcap.length) {
      int capturedLength = int32(pcap, recordOffset + 8);
      int frameOffset = recordOffset + 16;
      int ipOffset = frameOffset + 14;
      int ipHeaderLength = (pcap[ipOffset] & 0x0F) * 4;
      int udpOffset = ipOffset + ipHeaderLength;
      int udpLength = networkUInt16(pcap, udpOffset + 4);
      int payloadOffset = udpOffset + 8;
      int payloadLength = udpLength - 8;

      datagram.clear();
      datagram.put(pcap, payloadOffset, payloadLength);
      datagram.flip();
      accumulator.onPacket(datagram);
      assertEquals(
          UdpPacketHeaderCodec.messageCount(datagram, 0), dispatcher.dispatch(datagram, 0));

      recordOffset = frameOffset + capturedLength;
    }
    assertEquals(pcap.length, recordOffset);

    String actual =
        "sha256="
            + HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(pcap))
            + "\n"
            + accumulator.trace();
    String expected = new String(resourceBytes(TRACE), java.nio.charset.StandardCharsets.UTF_8);
    assertEquals(expected.replace("\r\n", "\n"), actual);
  }

  private static byte[] resourceBytes(String name) throws IOException {
    try (InputStream input = OfficialPcapReplayTest.class.getResourceAsStream(name)) {
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

  private static final class ReplayAccumulator implements MarketDataMessageHandler {

    private final int[] templates = new int[203];
    private final int[] packetTypes = new int[6];
    private final int[] channels = new int[8];
    private int packets;
    private int messages;
    private long firstPacketSequence;
    private int firstPacketChannel;
    private int firstPacketType;
    private int firstPacketCount;
    private long firstInstrument;
    private long firstTimestamp;
    private long firstAnchor;
    private long firstIndex;
    private final byte[] firstName = new byte[128];
    private int firstNameLength;

    void onPacket(ByteBuffer packet) {
      if (packets == 0) {
        firstPacketSequence = UdpPacketHeaderCodec.sequenceNumber(packet, 0);
        firstPacketChannel = UdpPacketHeaderCodec.channelId(packet, 0);
        firstPacketType = UdpPacketHeaderCodec.type(packet, 0);
        firstPacketCount = UdpPacketHeaderCodec.messageCount(packet, 0);
      }
      int type = UdpPacketHeaderCodec.type(packet, 0);
      int channel = UdpPacketHeaderCodec.channelId(packet, 0);
      packetTypes[type]++;
      channels[channel - 44_849]++;
      packets++;
    }

    @Override
    public void onMessage(
        ByteBuffer buffer, int messageOffset, int templateId, long sequenceNumber) {
      templates[templateId]++;
      if (messages == 0) {
        firstInstrument = SnapshotHeaderDecoder.instrumentId(buffer, messageOffset);
        firstTimestamp =
            SnapshotHeaderDecoder.incrementalTimestampNanos(buffer, messageOffset);
        firstAnchor =
            SnapshotHeaderDecoder.incrementalSequenceNumber(buffer, messageOffset);
      } else if (messages == 1) {
        firstIndex = InstrumentDefinitionDecoder.indexId(buffer, messageOffset);
        firstNameLength = InstrumentDefinitionDecoder.nameLength(buffer, messageOffset);
        for (int index = 0; index < firstNameLength; index++) {
          firstName[index] =
              (byte) InstrumentDefinitionDecoder.nameByte(buffer, messageOffset, index);
        }
      }
      messages++;
    }

    String trace() {
      StringBuilder trace = new StringBuilder(1024);
      trace.append("packets=").append(packets).append('\n');
      trace.append("messages=").append(messages).append('\n');
      for (int type = 0; type < packetTypes.length; type++) {
        if (packetTypes[type] != 0) {
          trace.append("packetType.").append(type).append('=').append(packetTypes[type]).append('\n');
        }
      }
      for (int index = 0; index < channels.length; index++) {
        trace
            .append("channel.")
            .append(index + 44_849)
            .append('=')
            .append(channels[index])
            .append('\n');
      }
      for (int template = 0; template < templates.length; template++) {
        if (templates[template] != 0) {
          trace
              .append("template.")
              .append(template)
              .append('=')
              .append(templates[template])
              .append('\n');
        }
      }
      trace
          .append("firstPacket=sequence:")
          .append(firstPacketSequence)
          .append(",channel:")
          .append(firstPacketChannel)
          .append(",type:")
          .append(firstPacketType)
          .append(",count:")
          .append(firstPacketCount)
          .append('\n');
      trace
          .append("firstSnapshot=instrument:")
          .append(firstInstrument)
          .append(",timestamp:")
          .append(firstTimestamp)
          .append(",anchor:")
          .append(firstAnchor)
          .append(",name:")
          .append(new String(firstName, 0, firstNameLength, java.nio.charset.StandardCharsets.US_ASCII))
          .append(",index:")
          .append(firstIndex)
          .append('\n');
      return trace.toString();
    }
  }
}
