package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.UdpPacketHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.marketdata.EndOfCycleDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.RetransmitRejectDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.RetransmitRequestEncoder;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class RetransmitClientTest {

  public void testPagesByActualReturnedCountRatherThanRequestedCount() throws Exception {
    ScriptedTransport transport = new ScriptedTransport();
    long sequence = 10_000L;
    for (int page = 0; page < 6; page++) {
      transport.responses.add(successPacket(sequence + page * 50L, 50));
    }
    int[] delivered = {0};
    RetransmitClient client =
        new RetransmitClient(
            transport,
            () -> 123_456L,
            Duration.ofMillis(10),
            2,
            1400,
            (buffer, offset, templateId, messageSequence) -> delivered[0]++);

    int result = client.recover(sequence, 300);

    assertEquals(RetransmitClient.COMPLETE, result);
    assertEquals(List.of(255, 250, 200, 150, 100, 50), transport.requestedCounts);
    assertEquals(
        List.of(10_000L, 10_050L, 10_100L, 10_150L, 10_200L, 10_250L),
        transport.requestedSequences);
    assertEquals(300, delivered[0]);
    assertEquals(6, client.requestCount());
    assertEquals(0, client.retryCount());
    assertEquals(10_300L, client.nextSequence());
  }

  public void testRetriesSamePageUntilTimeoutBudgetIsExhausted() throws Exception {
    ScriptedTransport transport = new ScriptedTransport();
    RetransmitClient client =
        new RetransmitClient(
            transport,
            () -> 9L,
            Duration.ofMillis(1),
            2,
            1400,
            (buffer, offset, templateId, sequence) -> {});

    int result = client.recover(77L, 10);

    assertEquals(RetransmitClient.UNRECOVERABLE, result);
    assertEquals(RetransmitClient.FAILURE_TIMEOUT, client.failureReason());
    assertEquals(List.of(10, 10, 10), transport.requestedCounts);
    assertEquals(List.of(77L, 77L, 77L), transport.requestedSequences);
    assertEquals(3, client.requestCount());
    assertEquals(2, client.retryCount());
    assertEquals(Duration.ofMillis(1).toNanos(), transport.lastTimeoutNanos);
  }

  public void testRejectPreservesPinnedReasonAndRetryDelayAsUnrecoverable() throws Exception {
    ScriptedTransport transport = new ScriptedTransport();
    transport.responses.add(
        rejectPacket(
            500L,
            RetransmitRejectDecoder.REASON_SEQUENCE_TOO_LOW,
            987_654L));
    RetransmitClient client =
        new RetransmitClient(
            transport,
            () -> 1L,
            Duration.ofSeconds(1),
            3,
            1400,
            (buffer, offset, templateId, sequence) -> {});

    int result = client.recover(500L, 20);

    assertEquals(RetransmitClient.UNRECOVERABLE, result);
    assertEquals(
        RetransmitRejectDecoder.REASON_SEQUENCE_TOO_LOW, client.failureReason());
    assertEquals(987_654L, client.retryDelayNanos());
    assertEquals(1, client.requestCount());
    assertEquals(0, client.retryCount());
  }

  public void testCorruptOrNonProgressingResponsesFailClosed() {
    ScriptedTransport wrongSequence = new ScriptedTransport();
    wrongSequence.responses.add(successPacket(101L, 1));
    RetransmitClient first = client(wrongSequence);
    assertThrows(StarbaseProtocolException.class, () -> first.recover(100L, 1));

    ScriptedTransport tooMany = new ScriptedTransport();
    tooMany.responses.add(successPacket(100L, 2));
    RetransmitClient second = client(tooMany);
    assertThrows(StarbaseProtocolException.class, () -> second.recover(100L, 1));

    assertThrows(IllegalArgumentException.class, () -> client(new ScriptedTransport()).recover(-1, 1));
    assertThrows(IllegalArgumentException.class, () -> client(new ScriptedTransport()).recover(1, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> client(new ScriptedTransport()).recover(Long.MAX_VALUE, 2));
  }

  private static RetransmitClient client(ScriptedTransport transport) {
    return new RetransmitClient(
        transport,
        () -> 1L,
        Duration.ofMillis(1),
        0,
        1400,
        (buffer, offset, templateId, sequence) -> {});
  }

  private static ByteBuffer successPacket(long sequence, int count) {
    int messageLength = 24;
    ByteBuffer buffer =
        ByteBuffer.allocateDirect(24 + count * messageLength)
            .order(ByteOrder.LITTLE_ENDIAN);
    UdpPacketHeaderCodec.encode(
        buffer,
        0,
        1L,
        sequence,
        44849,
        UdpPacketHeaderCodec.TYPE_RETRANSMIT_SUCCESS,
        count);
    for (int index = 0; index < count; index++) {
      int offset = 24 + index * messageLength;
      buffer.putShort(offset, (short) messageLength);
      buffer.putShort(offset + 2, (short) EndOfCycleDecoder.TEMPLATE_ID);
      buffer.putShort(offset + 4, (short) 1);
      buffer.putLong(offset + 8, index);
      buffer.putInt(offset + MarketDataMessageHeaderCodec.ENCODED_LENGTH, 1);
    }
    return buffer;
  }

  private static ByteBuffer rejectPacket(long sequence, int reason, long retryDelay) {
    int messageLength =
        MarketDataMessageHeaderCodec.ENCODED_LENGTH + RetransmitRejectDecoder.BLOCK_LENGTH;
    ByteBuffer buffer =
        ByteBuffer.allocateDirect(UdpPacketHeaderCodec.ENCODED_LENGTH + messageLength)
            .order(ByteOrder.LITTLE_ENDIAN);
    UdpPacketHeaderCodec.encode(
        buffer, 0, 1L, sequence, 44849, UdpPacketHeaderCodec.TYPE_CONTROL, 1);
    int offset = UdpPacketHeaderCodec.ENCODED_LENGTH;
    buffer.putShort(offset, (short) messageLength);
    buffer.putShort(offset + 2, (short) RetransmitRejectDecoder.TEMPLATE_ID);
    buffer.putShort(offset + 4, (short) 1);
    int body = offset + MarketDataMessageHeaderCodec.ENCODED_LENGTH;
    buffer.putLong(body + RetransmitRejectDecoder.RETRY_DELAY_NANOS_OFFSET, retryDelay);
    buffer.put(body + RetransmitRejectDecoder.REASON_OFFSET, (byte) reason);
    return buffer;
  }

  private static final class ScriptedTransport implements RetransmitTransport {
    private final ArrayDeque<ByteBuffer> responses = new ArrayDeque<>();
    private final List<Long> requestedSequences = new ArrayList<>();
    private final List<Integer> requestedCounts = new ArrayList<>();
    private long lastTimeoutNanos;

    @Override
    public void send(ByteBuffer request, int length) {
      assertEquals(RetransmitRequestEncoder.ENCODED_LENGTH, length);
      requestedSequences.add(request.getLong(MarketDataMessageHeaderCodec.ENCODED_LENGTH));
      requestedCounts.add(
          Byte.toUnsignedInt(
              request.get(
                  MarketDataMessageHeaderCodec.ENCODED_LENGTH
                      + RetransmitRequestEncoder.MESSAGE_COUNT_OFFSET)));
    }

    @Override
    public int receive(ByteBuffer response, long timeoutNanos) throws IOException {
      lastTimeoutNanos = timeoutNanos;
      ByteBuffer scripted = responses.pollFirst();
      if (scripted == null) {
        return 0;
      }
      response.clear();
      for (int index = 0; index < scripted.capacity(); index++) {
        response.put(index, scripted.get(index));
      }
      response.limit(scripted.capacity());
      return scripted.capacity();
    }
  }
}
