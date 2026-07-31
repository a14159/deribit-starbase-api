package io.contek.invoker.deribit.starbase.orderentry.connection;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.HeartbeatCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.OrderEntryMessageHandler;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class TcpFrameAssemblerTest {

  private static volatile int sink;

  public void testHeaderSplitAcrossReadsDispatchesOnlyAfterTheCompleteFrameArrives() {
    ByteBuffer frame = ByteBuffer.allocate(HeartbeatCodec.MESSAGE_LENGTH)
        .order(ByteOrder.LITTLE_ENDIAN);
    HeartbeatCodec.encode(frame, 0, 17, 1, 0, 2);
    RecordingHandler handler = new RecordingHandler();
    TcpFrameAssembler assembler = new TcpFrameAssembler(256, handler);

    assembler.accept(frame, 0, 13);
    assertEquals(0, handler.calls);
    assertEquals(13, assembler.bufferedBytes());

    assembler.accept(frame, 13, HeartbeatCodec.MESSAGE_LENGTH - 13);
    assertEquals(1, handler.calls);
    assertEquals(HeartbeatCodec.TEMPLATE_ID, handler.templateId);
    assertEquals(0, assembler.bufferedBytes());
  }

  public void testSplitBodyCoalescedFramesAndTrailingPartialPreserveWireOrder() {
    ByteBuffer first = heartbeat(17);
    ByteBuffer second = heartbeat(18);
    ByteBuffer stream = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN);
    copy(first, 0, stream, 0, 40);
    copy(second, 0, stream, 40, 40);
    RecordingHandler handler = new RecordingHandler();
    TcpFrameAssembler assembler = new TcpFrameAssembler(128, handler);

    assembler.accept(stream, 0, 35);
    assertEquals(0, handler.calls);
    assembler.accept(stream, 35, 30);
    assertEquals(1, handler.calls);
    assertEquals(25, assembler.bufferedBytes());
    assembler.accept(stream, 65, 15);

    assertEquals(2, handler.calls);
    assertEquals(17, handler.firstCorrelationId);
    assertEquals(18, handler.lastCorrelationId);
    assertEquals(0, assembler.bufferedBytes());
  }

  public void testCleanEofIsIdempotentButPartialEofAndFurtherInputFailClosed() {
    RecordingHandler handler = new RecordingHandler();
    TcpFrameAssembler clean = new TcpFrameAssembler(64, handler);
    clean.accept(heartbeat(1), 0, 40);
    clean.endOfInput();
    clean.endOfInput();
    assertTrue(clean.isEnded());
    assertThrows(
        IllegalStateException.class, () -> clean.accept(heartbeat(2), 0, 40));

    TcpFrameAssembler truncated = new TcpFrameAssembler(64, handler);
    truncated.accept(heartbeat(3), 0, 39);
    assertThrows(StarbaseProtocolException.class, truncated::endOfInput);
    assertTrue(truncated.isEnded());
  }

  public void testInvalidHeaderLengthOversizedFrameAndNonzeroPaddingFailClosed() {
    RecordingHandler handler = new RecordingHandler();
    ByteBuffer shortLength = heartbeat(1);
    shortLength.putShort(TcpHeaderCodec.MESSAGE_LENGTH_OFFSET, (short) 31);
    assertThrows(
        StarbaseProtocolException.class,
        () -> new TcpFrameAssembler(64, handler).accept(shortLength, 0, 40));

    ByteBuffer oversized = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(oversized, 0, 0, 72, HeartbeatCodec.TEMPLATE_ID, 11, 1, 0, 2);
    assertThrows(
        StarbaseProtocolException.class,
        () -> new TcpFrameAssembler(64, handler).accept(oversized, 0, 32));

    ByteBuffer padded = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(padded, 0, 0, 41, HeartbeatCodec.TEMPLATE_ID, 11, 1, 0, 2);
    padded.putLong(32, 1);
    padded.put(41, (byte) 1);
    assertThrows(
        StarbaseProtocolException.class,
        () -> new TcpFrameAssembler(64, handler).accept(padded, 0, 48));
    assertEquals(0, handler.calls);
  }

  public void testWarmedFrameAssemblyAndDispatchAllocateNothing() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ByteBuffer frame = heartbeat(17);
    RecordingHandler handler = new RecordingHandler();
    TcpFrameAssembler assembler = new TcpFrameAssembler(64, handler);
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      exercise(assembler, frame, handler);
    }
    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(assembler, frame, handler);
    }
    assertEquals(
        0L,
        bean.getThreadAllocatedBytes(threadId) - before,
        "TCP frame assembly and dispatch allocated bytes");
  }

  private static ByteBuffer heartbeat(long correlationId) {
    ByteBuffer frame =
        ByteBuffer.allocateDirect(HeartbeatCodec.MESSAGE_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
    HeartbeatCodec.encode(frame, 0, correlationId, 1, 0, 2);
    return frame;
  }

  private static void copy(
      ByteBuffer source, int sourceOffset, ByteBuffer target, int targetOffset, int length) {
    for (int index = 0; index < length; index++) {
      target.put(targetOffset + index, source.get(sourceOffset + index));
    }
  }

  private static void exercise(
      TcpFrameAssembler assembler, ByteBuffer frame, RecordingHandler handler) {
    assembler.accept(frame, 0, HeartbeatCodec.MESSAGE_LENGTH);
    sink = handler.calls;
  }

  private static final class RecordingHandler implements OrderEntryMessageHandler {
    private int calls;
    private int templateId;
    private long firstCorrelationId = Long.MIN_VALUE;
    private long lastCorrelationId;

    @Override
    public void onMessage(int templateId, ByteBuffer buffer, int offset) {
      calls++;
      this.templateId = templateId;
      lastCorrelationId = HeartbeatCodec.correlationId(buffer, offset);
      if (firstCorrelationId == Long.MIN_VALUE) {
        firstCorrelationId = lastCorrelationId;
      }
    }
  }
}
