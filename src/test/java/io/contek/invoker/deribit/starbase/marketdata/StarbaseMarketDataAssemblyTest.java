package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.StarbaseApiFactory;
import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.UdpPacketHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidPutDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.EndOfCycleDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.SnapshotHeaderDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.SnapshotTrailerDecoder;
import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayDeque;
import java.lang.management.ManagementFactory;

public final class StarbaseMarketDataAssemblyTest {

  private static volatile long allocationSink;

  public void testPublicFactoryRequiresAnExactABPairForRedundantAssembly() {
    StarbaseMarketDataContext a = context(ProductGroup.BTC, GatewaySide.A, 4200);
    StarbaseMarketDataContext b = context(ProductGroup.BTC, GatewaySide.B, 4300);
    StarbaseApiFactory factory = new StarbaseApiFactory();

    StarbaseMarketDataApi api = factory.marketData(a, b);

    assertTrue(api.isRedundant());
    assertEquals(a, api.context(GatewaySide.A));
    assertEquals(b, api.context(GatewaySide.B));
    assertThrows(IllegalArgumentException.class, () -> factory.marketData(a, a));
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.marketData(a, context(ProductGroup.ETH, GatewaySide.B, 4400)));
  }

  public void testSnapshotABArbitrationAndRetransmitRecoveryPublishOneCoherentBook()
      throws Exception {
    ScriptedTransport retransmitA = new ScriptedTransport();
    ScriptedTransport retransmitB = new ScriptedTransport();
    StarbaseMarketDataApi api = api(retransmitA, retransmitB);
    long[] quantities = new long[8];
    int[] count = new int[1];
    api.getOrderBookChannel(100L)
        .addListener(
            (price, quantity, timestamp) -> {
              if (price != Long.MIN_VALUE) {
                quantities[count[0]++] = quantity;
              }
            });

    synchronize(api, 1L, 99L, 10L);
    assertTrue(api.isSynchronized());
    assertEquals(10L, quantities[count[0] - 1]);

    ByteBuffer live = bidPutPacket(100L, 2L, 20L, 100L, 2L);
    api.acceptIncrementalPacket(GatewaySide.A, live);
    api.acceptIncrementalPacket(GatewaySide.B, live);
    assertEquals(30L, quantities[count[0] - 1]);
    assertEquals(1L, api.incrementalDiagnostics(GatewaySide.B).duplicates());

    retransmitA.responses.add(bidPutPacket(101L, 3L, 5L, 100L, 3L, true));
    api.acceptIncrementalPacket(
        GatewaySide.A, bidPutPacket(102L, 4L, 7L, 100L, 4L));

    assertEquals(42L, quantities[count[0] - 1]);
    assertEquals(1, retransmitA.sendCount);
    assertEquals(101L, retransmitA.requestBeginSequence);
    assertEquals(1, retransmitA.requestMessageCount);
    assertEquals(1L, api.incrementalDiagnostics(GatewaySide.A).gaps());
    assertEquals(1L, api.incrementalDiagnostics(GatewaySide.A).retransmitRequests());
    assertTrue(api.isSynchronized());
  }

  public void testUnrecoverableGapInvalidatesAndOnlyAFreshCompleteCycleRestoresState()
      throws Exception {
    StarbaseMarketDataApi api = api(new ScriptedTransport(), new ScriptedTransport());
    long[] invalidations = new long[1];
    long[] lastQuantity = new long[1];
    api.getOrderBookChannel(100L)
        .addListener(
            (price, quantity, timestamp) -> {
              if (price == Long.MIN_VALUE) {
                invalidations[0]++;
              } else {
                lastQuantity[0] = quantity;
              }
            });
    synchronize(api, 1L, 99L, 10L);

    api.acceptIncrementalPacket(
        GatewaySide.A, bidPutPacket(100L, 2L, 20L, 100L, 2L));
    api.acceptIncrementalPacket(
        GatewaySide.A, bidPutPacket(102L, 3L, 7L, 100L, 3L));

    assertTrue(api.requiresFreshSnapshot());
    assertFalse(api.isSynchronized());
    assertFalse(api.isOrderBookReady(100L));
    assertEquals(2L, invalidations[0]);

    long snapshotSequence = 6L;
    api.acceptSnapshotPacket(GatewaySide.A, endOfCyclePacket(snapshotSequence++));
    api.acceptSnapshotPacket(
        GatewaySide.A, snapshotBoundaryPacket(snapshotSequence++, true, 102L));
    api.acceptSnapshotPacket(
        GatewaySide.A, snapshotBidPutPacket(snapshotSequence++, 9L, 99L, 110L, 9L));
    api.acceptSnapshotPacket(
        GatewaySide.A, snapshotBoundaryPacket(snapshotSequence++, false, 102L));
    assertFalse(api.isSynchronized());
    api.acceptSnapshotPacket(GatewaySide.A, endOfCyclePacket(snapshotSequence));

    assertFalse(api.requiresFreshSnapshot());
    assertTrue(api.isSynchronized());
    assertTrue(api.isOrderBookReady(100L));
    assertEquals(99L, lastQuantity[0]);
  }

  public void testIncrementalOverlapRemainsInvisibleUntilAtomicSnapshotPublication() {
    StarbaseMarketDataApi api = api(new ScriptedTransport(), new ScriptedTransport());
    long[] lastQuantity = new long[1];
    int[] publishedLevels = new int[1];
    api.getOrderBookChannel(100L)
        .addListener(
            (price, quantity, timestamp) -> {
              if (price != Long.MIN_VALUE) {
                publishedLevels[0]++;
                lastQuantity[0] = quantity;
              }
            });
    api.acceptIncrementalPacket(GatewaySide.A, incrementalEndOfCyclePacket(99L));
    api.acceptSnapshotPacket(GatewaySide.A, endOfCyclePacket(1L));
    api.acceptSnapshotPacket(GatewaySide.A, snapshotBoundaryPacket(2L, true, 99L));
    api.acceptSnapshotPacket(GatewaySide.A, snapshotBidPutPacket(3L, 1L, 10L, 100L, 1L));

    api.acceptIncrementalPacket(
        GatewaySide.A, bidPutPacket(100L, 2L, 20L, 100L, 2L));
    assertEquals(0, publishedLevels[0]);

    api.acceptSnapshotPacket(GatewaySide.A, snapshotBoundaryPacket(4L, false, 99L));
    assertEquals(0, publishedLevels[0]);
    api.acceptSnapshotPacket(GatewaySide.A, endOfCyclePacket(5L));

    assertTrue(api.isSynchronized());
    assertEquals(1, publishedLevels[0]);
    assertEquals(30L, lastQuantity[0]);
  }

  public void testLateBookConfigurationClosesSynchronizationAndRequestsFreshCycle() {
    StarbaseMarketDataApi api = api(new ScriptedTransport(), new ScriptedTransport());
    synchronize(api, 1L, 99L, 10L);
    api.instrumentRegistry()
        .upsert(
            200L,
            "BTC-SECOND",
            ProductGroup.BTC,
            "BTC",
            "USD",
            -8,
            1L,
            1L,
            0,
            0,
            1);

    api.configureOrderBook(200L, 8, 8);

    assertTrue(api.requiresFreshSnapshot());
    assertFalse(api.isSynchronized());
    assertFalse(api.isOrderBookReady(100L));
    assertFalse(api.isOrderBookReady(200L));
  }

  public void testUnsupportedStateChangeFailsOnlyTheAffectedInputAndClosesReadiness() {
    StarbaseMarketDataApi api = api(new ScriptedTransport(), new ScriptedTransport());
    synchronize(api, 1L, 99L, 10L);

    assertThrows(
        StarbaseProtocolException.class,
        () -> api.acceptIncrementalPacket(GatewaySide.A, unsupportedPacket(100L)));

    assertEquals(FeedDiagnostics.UNHEALTHY, api.incrementalDiagnostics(GatewaySide.A).health());
    assertEquals(1L, api.incrementalDiagnostics(GatewaySide.A).unknownTemplates());
    assertFalse(api.incrementalDiagnostics(GatewaySide.B).health() == FeedDiagnostics.UNHEALTHY);
    assertFalse(api.isSynchronized());
    assertFalse(api.isOrderBookReady(100L));
  }

  public void testLiveArbitrationDecodeAndBookMutationAllocateNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    StarbaseMarketDataApi api = api(new ScriptedTransport(), new ScriptedTransport());
    synchronize(api, 1L, 99L, 10L);
    ByteBuffer packet = bidPutPacket(100L, 2L, 20L, 100L, 2L);
    long sequence = exerciseLive(api, packet, 100L, 20_000);

    long before = bean.getThreadAllocatedBytes(threadId);
    sequence = exerciseLive(api, packet, sequence, 100_000);
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    allocationSink = sequence;
    assertEquals(0L, allocated);
  }

  private static StarbaseMarketDataApi api(
      RetransmitTransport retransmitA, RetransmitTransport retransmitB) {
    StarbaseMarketDataApi api =
        new StarbaseMarketDataApi(
            context(ProductGroup.BTC, GatewaySide.A, 4200),
            context(ProductGroup.BTC, GatewaySide.B, 4300),
            4,
            retransmitA,
            retransmitB,
            0);
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
    api.configureOrderBook(100L, 16, 16);
    return api;
  }

  private static void synchronize(
      StarbaseMarketDataApi api, long snapshotSequence, long anchor, long quantity) {
    api.acceptIncrementalPacket(GatewaySide.A, incrementalEndOfCyclePacket(anchor));
    api.acceptSnapshotPacket(GatewaySide.A, endOfCyclePacket(snapshotSequence++));
    api.acceptSnapshotPacket(
        GatewaySide.A, snapshotBoundaryPacket(snapshotSequence++, true, anchor));
    ByteBuffer snapshotPut =
        snapshotBidPutPacket(snapshotSequence++, 1L, quantity, 100L, 1L);
    api.acceptSnapshotPacket(GatewaySide.A, snapshotPut);
    api.acceptSnapshotPacket(GatewaySide.B, snapshotPut);
    api.acceptSnapshotPacket(
        GatewaySide.A, snapshotBoundaryPacket(snapshotSequence++, false, anchor));
    api.acceptSnapshotPacket(GatewaySide.A, endOfCyclePacket(snapshotSequence));
  }

  private static StarbaseMarketDataContext context(
      ProductGroup productGroup, GatewaySide side, int basePort) {
    return new StarbaseMarketDataContext(
        productGroup,
        side,
        "loopback",
        new InetSocketAddress("127.0.0.1", basePort),
        new InetSocketAddress("127.0.0.1", basePort + 1),
        new InetSocketAddress("127.0.0.1", basePort + 2),
        4096,
        4096,
        Duration.ofMillis(1),
        IoPolicy.BLOCKING,
        () -> 1L);
  }

  private static ByteBuffer snapshotBoundaryPacket(
      long packetSequence, boolean header, long anchor) {
    int templateId = header ? SnapshotHeaderDecoder.TEMPLATE_ID : SnapshotTrailerDecoder.TEMPLATE_ID;
    int flags =
        header
            ? MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION
            : MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION;
    ByteBuffer packet = packet(packetSequence, UdpPacketHeaderCodec.TYPE_SNAPSHOT, templateId, flags, 24);
    int body = UdpPacketHeaderCodec.ENCODED_LENGTH + MarketDataMessageHeaderCodec.ENCODED_LENGTH;
    packet.putLong(body, 100L);
    packet.putLong(body + 8, 1_000L);
    packet.putLong(body + 16, anchor);
    return packet;
  }

  private static ByteBuffer endOfCyclePacket(long packetSequence) {
    ByteBuffer packet =
        packet(
            packetSequence,
            UdpPacketHeaderCodec.TYPE_SNAPSHOT,
            EndOfCycleDecoder.TEMPLATE_ID,
            0,
            EndOfCycleDecoder.BLOCK_LENGTH);
    packet.putInt(
        UdpPacketHeaderCodec.ENCODED_LENGTH + MarketDataMessageHeaderCodec.ENCODED_LENGTH,
        1);
    return packet;
  }

  private static ByteBuffer incrementalEndOfCyclePacket(long packetSequence) {
    ByteBuffer packet = endOfCyclePacket(packetSequence);
    packet.putShort(
        UdpPacketHeaderCodec.TYPE_OFFSET,
        (short) UdpPacketHeaderCodec.TYPE_INCREMENTAL_UPDATE);
    return packet;
  }

  private static ByteBuffer bidPutPacket(
      long packetSequence,
      long orderId,
      long quantity,
      long price,
      long sortOrderId) {
    return bidPutPacket(packetSequence, orderId, quantity, price, sortOrderId, false);
  }

  private static ByteBuffer snapshotBidPutPacket(
      long packetSequence,
      long orderId,
      long quantity,
      long price,
      long sortOrderId) {
    ByteBuffer packet =
        bidPutPacket(packetSequence, orderId, quantity, price, sortOrderId, false);
    packet.putShort(UdpPacketHeaderCodec.TYPE_OFFSET, (short) UdpPacketHeaderCodec.TYPE_SNAPSHOT);
    return packet;
  }

  private static ByteBuffer bidPutPacket(
      long packetSequence,
      long orderId,
      long quantity,
      long price,
      long sortOrderId,
      boolean retransmit) {
    int type =
        retransmit
            ? UdpPacketHeaderCodec.TYPE_RETRANSMIT_SUCCESS
            : UdpPacketHeaderCodec.TYPE_INCREMENTAL_UPDATE;
    ByteBuffer packet =
        packet(
            packetSequence,
            type,
            BidPutDecoder.TEMPLATE_ID,
            MarketDataMessageHeaderCodec.FLAG_START_OF_TRANSACTION
                | MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION,
            BidPutDecoder.BLOCK_LENGTH);
    int body = UdpPacketHeaderCodec.ENCODED_LENGTH + MarketDataMessageHeaderCodec.ENCODED_LENGTH;
    packet.putLong(body, orderId);
    packet.putLong(body + 8, 100L);
    packet.putLong(body + 16, quantity);
    packet.putLong(body + 24, price);
    packet.putLong(body + 32, sortOrderId);
    return packet;
  }

  private static ByteBuffer unsupportedPacket(long packetSequence) {
    return packet(
        packetSequence,
        UdpPacketHeaderCodec.TYPE_INCREMENTAL_UPDATE,
        33,
        0,
        16);
  }

  private static long exerciseLive(
      StarbaseMarketDataApi api, ByteBuffer packet, long sequence, int iterations) {
    int message = UdpPacketHeaderCodec.ENCODED_LENGTH;
    int body = message + MarketDataMessageHeaderCodec.ENCODED_LENGTH;
    for (int iteration = 0; iteration < iterations; iteration++) {
      packet.putLong(UdpPacketHeaderCodec.SEQUENCE_NUMBER_OFFSET, sequence);
      packet.putLong(message + MarketDataMessageHeaderCodec.TRANSACT_TIME_NANOS_OFFSET, sequence);
      packet.putLong(body + 16, (iteration & 1) == 0 ? 20L : 21L);
      allocationSink += api.acceptIncrementalPacket(GatewaySide.A, packet);
      sequence++;
    }
    return sequence;
  }

  private static ByteBuffer packet(
      long sequence, int type, int templateId, int flags, int bodyLength) {
    int messageLength = MarketDataMessageHeaderCodec.ENCODED_LENGTH + bodyLength;
    ByteBuffer packet =
        ByteBuffer.allocateDirect(UdpPacketHeaderCodec.ENCODED_LENGTH + messageLength)
            .order(ByteOrder.LITTLE_ENDIAN);
    UdpPacketHeaderCodec.encode(packet, 0, 1L, sequence, 1, type, 1);
    int message = UdpPacketHeaderCodec.ENCODED_LENGTH;
    packet.putShort(message, (short) messageLength);
    packet.putShort(message + 2, (short) templateId);
    packet.putShort(message + 4, (short) 1);
    packet.putShort(message + 6, (short) flags);
    packet.putLong(message + 8, sequence);
    packet.limit(packet.capacity());
    return packet;
  }

  private static final class ScriptedTransport implements RetransmitTransport {
    private final ArrayDeque<ByteBuffer> responses = new ArrayDeque<>();
    private int sendCount;
    private long requestBeginSequence;
    private int requestMessageCount;

    @Override
    public void send(ByteBuffer request, int length) {
      sendCount++;
      int body = MarketDataMessageHeaderCodec.ENCODED_LENGTH;
      requestBeginSequence = request.getLong(body);
      requestMessageCount = Byte.toUnsignedInt(request.get(body + 8));
    }

    @Override
    public int receive(ByteBuffer response, long timeoutNanos) throws IOException {
      ByteBuffer scripted = responses.pollFirst();
      if (scripted == null) {
        return 0;
      }
      response.clear();
      for (int index = 0; index < scripted.limit(); index++) {
        response.put(index, scripted.get(index));
      }
      response.limit(scripted.limit());
      return scripted.limit();
    }
  }
}
