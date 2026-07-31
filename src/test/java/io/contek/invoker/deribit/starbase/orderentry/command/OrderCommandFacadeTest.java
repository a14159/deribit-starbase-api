package io.contek.invoker.deribit.starbase.orderentry.command;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.AmendOrderRequestDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.CancelOrderRequestDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.MassCancelRequestDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.NewOrderRequestDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.NewOrderRequestEncoder;
import java.lang.management.ManagementFactory;
import io.contek.invoker.deribit.starbase.orderentry.connection.SessionSequenceState;
import io.contek.invoker.deribit.starbase.orderentry.connection.TcpFrameTransport;
import io.contek.invoker.deribit.starbase.orderentry.connection.TcpFrameWriter;
import io.contek.invoker.deribit.starbase.orderentry.state.CorrelationTable;
import java.nio.ByteBuffer;

public final class OrderCommandFacadeTest {

  private static volatile long sink;

  public void testReadyLimitOrderEncodesSendsAndCorrelatesExactlyOnce() {
    CapturingTransport transport = new CapturingTransport();
    TcpFrameWriter writer = new TcpFrameWriter(256, transport);
    CorrelationTable correlations = new CorrelationTable(4, 100);
    SessionSequenceState sequences = new SessionSequenceState(5, 7);
    OrderCommandFacade commands =
        new OrderCommandFacade(() -> true, writer, correlations, sequences, () -> 1_000, 50);

    long correlationId =
        commands.newLimit(
            101, 102, -5_000_000_000L, 25, -2, true, 0, 103, 1, 0, 1, 0);

    assertEquals(100, correlationId);
    assertEquals(CorrelationTable.STATE_PENDING, correlations.state(correlationId));
    assertEquals(OrderCommandFacade.COMMAND_NEW, correlations.commandType(correlationId));
    assertEquals(101, correlations.clientOrderId(correlationId));
    assertEquals(1_050, correlations.deadlineNanos(correlationId));
    assertEquals(1, transport.writes);
    assertEquals(101, transport.clientOrderId);
    assertEquals(100, transport.correlationId);
    assertEquals(7, transport.sequence);
    assertEquals(4, transport.lastProcessedSequence);
    assertEquals(1_000, transport.sendTimeNanos);
  }

  public void testMarketAmendCancelAndMassCancelUseExactTemplatesAndFreshCorrelations() {
    RecordingTransport transport = new RecordingTransport();
    CorrelationTable correlations = new CorrelationTable(8, 200);
    OrderCommandFacade commands = commands(transport, correlations, new SessionSequenceState(3, 10));

    assertEquals(200, commands.newMarket(11, 12, 13, -2, true, 0, 14, -1, -2, 0, 1));
    assertEquals(NewOrderRequestEncoder.TEMPLATE_ID, transport.templateId);
    assertTrue(transport.market);
    assertEquals(11, transport.clientOrderId);
    assertEquals(201, commands.amend(11, 12, 15, 16, -2, true, 0, 2));
    assertEquals(110, transport.templateId);
    assertEquals(15, transport.priceMantissa);
    assertEquals(202, commands.cancel(11, 12));
    assertEquals(120, transport.templateId);
    assertEquals(203, commands.massCancel(Long.MIN_VALUE, 12, 3, 0));
    assertEquals(140, transport.templateId);
    assertEquals(12, transport.instrumentId);
    assertEquals(4, correlations.size());
    assertEquals(13, transport.sequence);
  }

  public void testNotReadyAndPendingFrameRejectBeforeCorrelationOrSequenceMutation() {
    CorrelationTable correlations = new CorrelationTable(4, 1);
    SessionSequenceState sequences = new SessionSequenceState(1, 1);
    OrderCommandFacade unavailable =
        new OrderCommandFacade(() -> false, new TcpFrameWriter(256, (b, o, l) -> l),
            correlations, sequences, () -> 1, 10);
    assertThrows(
        IllegalStateException.class,
        () -> unavailable.newLimit(1, 2, 3, 4, -1, true, 0, 0, 1, 0, 0, 0));
    assertEquals(0, correlations.size());
    assertEquals(1, sequences.nextOutbound());

    TcpFrameWriter blockedWriter = new TcpFrameWriter(256, (b, o, l) -> 0);
    OrderCommandFacade blocked =
        new OrderCommandFacade(() -> true, blockedWriter, correlations, sequences, () -> 1, 10);
    long first = blocked.newLimit(1, 2, 3, 4, -1, true, 0, 0, 1, 0, 0, 0);
    assertTrue(blockedWriter.pendingBytes() > 0);
    assertThrows(
        IllegalStateException.class,
        () -> blocked.cancel(1, 2));
    assertEquals(1, correlations.size());
    assertEquals(CorrelationTable.STATE_PENDING, correlations.state(first));
    assertEquals(2, sequences.nextOutbound());
  }

  public void testInvalidArgumentsDoNotConsumeCorrelationOrSequence() {
    CorrelationTable correlations = new CorrelationTable(2, 50);
    SessionSequenceState sequences = new SessionSequenceState(1, 9);
    OrderCommandFacade commands = commands((b, o, l) -> l, correlations, sequences);

    assertThrows(
        IllegalArgumentException.class,
        () -> commands.newLimit(1, 2, Long.MIN_VALUE, 4, -1, true, 0, 0, 1, 0, 0, 0));
    assertEquals(0, correlations.size());
    assertEquals(9, sequences.nextOutbound());
    assertEquals(50, commands.cancel(1, 2));
  }

  public void testPartialWriteIsResumedWithoutCreatingASecondCommand() {
    PartialTransport transport = new PartialTransport();
    TcpFrameWriter writer = new TcpFrameWriter(256, transport);
    CorrelationTable correlations = new CorrelationTable(2, 1);
    OrderCommandFacade commands =
        new OrderCommandFacade(() -> true, writer, correlations,
            new SessionSequenceState(1, 1), () -> 1, 10);
    long correlation = commands.cancel(1, 2);
    assertTrue(writer.pendingBytes() > 0);
    transport.writable = true;
    assertTrue(writer.flush());
    assertEquals(1, correlations.size());
    assertEquals(CorrelationTable.STATE_PENDING, correlations.state(correlation));
    assertEquals(56, transport.totalWritten);
  }

  public void testTransportFailureReleasesCorrelationAndPoisonedWriterRejectsBeforeFurtherMutation() {
    TcpFrameWriter writer =
        new TcpFrameWriter(256, (buffer, offset, length) -> {
          throw new IllegalStateException("write failed");
        });
    CorrelationTable correlations = new CorrelationTable(2, 1);
    SessionSequenceState sequences = new SessionSequenceState(1, 1);
    OrderCommandFacade commands =
        new OrderCommandFacade(() -> true, writer, correlations, sequences, () -> 1, 10);

    assertThrows(IllegalStateException.class, () -> commands.cancel(1, 2));
    assertTrue(writer.isFailed());
    assertEquals(0, correlations.size());
    assertEquals(2, sequences.nextOutbound());
    assertThrows(IllegalStateException.class, () -> commands.cancel(1, 2));
    assertEquals(0, correlations.size());
    assertEquals(2, sequences.nextOutbound());
  }

  public void testWarmedNewOrderPathAllocatesNothing() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    CorrelationTable correlations = new CorrelationTable(1, 1);
    OrderCommandFacade commands =
        commands((b, o, l) -> l, correlations, new SessionSequenceState(1, 1));
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      exercise(commands, correlations, iteration + 1L);
    }
    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(commands, correlations, 1_000_001L + iteration);
    }
    assertEquals(0L, bean.getThreadAllocatedBytes(threadId) - before,
        "order command facade allocated bytes");
  }

  private static OrderCommandFacade commands(
      TcpFrameTransport transport,
      CorrelationTable correlations,
      SessionSequenceState sequences) {
    return new OrderCommandFacade(
        () -> true, new TcpFrameWriter(256, transport), correlations, sequences, () -> 1_000, 50);
  }

  private static void exercise(
      OrderCommandFacade commands, CorrelationTable correlations, long clientOrderId) {
    long correlation =
        commands.newLimit(clientOrderId, 2, 3, 4, -1, true, 0, 0, 1, 0, 0, 0);
    if (!correlations.release(correlation)) {
      throw new AssertionError("correlation release failed");
    }
    sink = correlation;
  }

  private static final class CapturingTransport implements TcpFrameTransport {
    private int writes;
    private long clientOrderId;
    private long correlationId;
    private long sequence;
    private long lastProcessedSequence;
    private long sendTimeNanos;

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      NewOrderRequestDecoder.validate(buffer, offset);
      writes++;
      clientOrderId = NewOrderRequestDecoder.clientOrderId(buffer, offset);
      correlationId = NewOrderRequestDecoder.correlationId(buffer, offset);
      sequence =
          io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec.sequenceNumber(
              buffer, offset);
      lastProcessedSequence =
          io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec
              .lastProcessedSequenceNumber(buffer, offset);
      sendTimeNanos =
          io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec.sendTimeNanos(buffer, offset);
      return length;
    }
  }

  private static final class RecordingTransport implements TcpFrameTransport {
    private int templateId;
    private long clientOrderId;
    private long instrumentId;
    private long priceMantissa;
    private long sequence;
    private boolean market;

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      templateId = TcpHeaderCodec.messageTypeId(buffer, offset);
      sequence = TcpHeaderCodec.sequenceNumber(buffer, offset);
      if (templateId == 100) {
        NewOrderRequestDecoder.validate(buffer, offset);
        clientOrderId = NewOrderRequestDecoder.clientOrderId(buffer, offset);
        instrumentId = NewOrderRequestDecoder.instrumentId(buffer, offset);
        market = NewOrderRequestDecoder.isMarket(buffer, offset);
      } else if (templateId == 110) {
        AmendOrderRequestDecoder.validate(buffer, offset);
        clientOrderId = AmendOrderRequestDecoder.clientOrderId(buffer, offset);
        instrumentId = AmendOrderRequestDecoder.instrumentId(buffer, offset);
        priceMantissa = AmendOrderRequestDecoder.priceMantissa(buffer, offset);
      } else if (templateId == 120) {
        CancelOrderRequestDecoder.validate(buffer, offset);
        clientOrderId = CancelOrderRequestDecoder.clientOrderId(buffer, offset);
        instrumentId = CancelOrderRequestDecoder.instrumentId(buffer, offset);
      } else if (templateId == 140) {
        MassCancelRequestDecoder.validate(buffer, offset);
        instrumentId = MassCancelRequestDecoder.instrumentId(buffer, offset);
      } else {
        throw new AssertionError("unexpected template " + templateId);
      }
      return length;
    }
  }

  private static final class PartialTransport implements TcpFrameTransport {
    private int totalWritten;
    private boolean writable;

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      if (totalWritten == 0) {
        totalWritten = 10;
        return 10;
      }
      if (!writable) {
        return 0;
      }
      totalWritten += length;
      return length;
    }
  }
}
