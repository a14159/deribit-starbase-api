package io.contek.invoker.deribit.starbase.codec.orderentry;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class OrderEntryMessageDispatcherTest {

  private static volatile int sink;

  public void testRoutesAnImplementedLifecycleTemplateAfterCompleteFrameValidation() {
    ByteBuffer frame = ByteBuffer.allocateDirect(88).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(
        frame, 0, 0, 88, OrdersCanceledDecoder.TEMPLATE_ID, 5, 1, 0, 2);
    int body = 32;
    frame.putLong(body, 3);
    frame.putLong(body + 8, 4);
    frame.put(body + 16, (byte) 1);
    frame.putShort(body + 17, (short) OrdersCanceledDecoder.ORDER_BLOCK_LENGTH);
    frame.putShort(body + 19, (short) 1);
    int order = body + 21;
    frame.putLong(order, 5);
    frame.putLong(order + 8, 6);
    frame.putLong(order + 16, 7);
    frame.putLong(order + 24, 8);
    frame.put(order + 32, (byte) -2);
    frame.put(order + 33, (byte) 17);
    frame.put(order + 34, (byte) 1);

    RecordingHandler handler = new RecordingHandler();
    OrderEntryMessageDispatcher.dispatch(frame, 0, handler);

    assertEquals(OrdersCanceledDecoder.TEMPLATE_ID, handler.templateId);
  }

  public void testRoutesCurrentLayoutsWithTheirPerMessageHeaderVersions() {
    RecordingHandler handler = new RecordingHandler();

    ByteBuffer confirmation = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
    LogonConfirmationCodec.encode(confirmation, 0, 30, 15, 1, 0, 10);
    confirmation.putShort(TcpHeaderCodec.VERSION_OFFSET, (short) 12);
    OrderEntryMessageDispatcher.dispatch(confirmation, 0, handler);

    ByteBuffer heartbeat = ByteBuffer.allocateDirect(48).order(ByteOrder.LITTLE_ENDIAN);
    HeartbeatCodec.encode(heartbeat, 0, 7, 2, 1, 11);
    heartbeat.putShort(TcpHeaderCodec.VERSION_OFFSET, (short) 0);
    OrderEntryMessageDispatcher.dispatch(heartbeat, 0, handler);

    OrderEntryMessageDispatcher.dispatch(orderPlacedFrame(8), 0, handler);

    assertEquals(OrderPlacedDecoder.TEMPLATE_ID, handler.templateId);
    assertEquals(3, handler.calls);
  }

  public void testRejectsOrderPlacedHeaderBeforeCurrentLayoutAndTruncatedCurrentLayout() {
    RecordingHandler handler = new RecordingHandler();
    ByteBuffer preLayout = orderPlacedFrame(7);
    assertThrows(
        StarbaseProtocolException.class,
        () -> OrderEntryMessageDispatcher.dispatch(preLayout, 0, handler));

    ByteBuffer truncated = orderPlacedFrame(8);
    truncated.putShort(TcpHeaderCodec.MESSAGE_LENGTH_OFFSET, (short) 120);
    assertThrows(
        StarbaseProtocolException.class,
        () -> OrderEntryMessageDispatcher.dispatch(truncated, 0, handler));
    assertEquals(0, handler.calls);
  }

  public void testEveryImplementedTemplateHasAnExplicitValidationRoute() {
    int[] implemented = {
      1, 2, 4, 5, 10, 11, 20, 21, 30,
      100, 110, 120, 125, 140,
      200, 202, 210, 212, 220, 222, 240, 242,
      300, 310, 312
    };
    RecordingHandler handler = new RecordingHandler();
    for (int templateId : implemented) {
      ByteBuffer frame = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
      TcpHeaderCodec.encode(frame, 0, 0, 32, templateId, 15, 1, 0, 2);
      RuntimeException failure =
          assertThrows(
              RuntimeException.class,
              () -> OrderEntryMessageDispatcher.dispatch(frame, 0, handler));
      assertFalse(
          failure.getMessage() != null
              && failure.getMessage().startsWith("unsupported order-entry templateId:"),
          "implemented template was not routed: " + templateId);
    }
  }

  public void testKnownButUnsupportedUnknownAndFutureVersionFramesFailClosedWithoutCallback() {
    RecordingHandler handler = new RecordingHandler();
    ByteBuffer knownUnsupported = headerOnlyFrame(130, 15);
    StarbaseProtocolException unsupported =
        assertThrows(
            StarbaseProtocolException.class,
            () -> OrderEntryMessageDispatcher.dispatch(knownUnsupported, 0, handler));
    assertTrue(unsupported.getMessage().startsWith("unsupported order-entry templateId:"));

    ByteBuffer unknown = headerOnlyFrame(9999, 11);
    assertThrows(
        StarbaseProtocolException.class,
        () -> OrderEntryMessageDispatcher.dispatch(unknown, 0, handler));

    ByteBuffer future = headerOnlyFrame(310, 16);
    assertThrows(
        StarbaseProtocolException.class,
        () -> OrderEntryMessageDispatcher.dispatch(future, 0, handler));
    assertEquals(0, handler.calls);
  }

  public void testValidDispatchAllocatesNothingAfterWarmup() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    ByteBuffer frame = canceledFrame();
    RecordingHandler handler = new RecordingHandler();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      exercise(frame, handler);
    }
    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(frame, handler);
    }
    assertEquals(
        0L,
        bean.getThreadAllocatedBytes(threadId) - before,
        "valid order-entry dispatch allocated bytes");
  }

  private static ByteBuffer headerOnlyFrame(int templateId, int version) {
    ByteBuffer frame = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(frame, 0, 0, 32, templateId, version, 1, 0, 2);
    return frame;
  }

  private static ByteBuffer canceledFrame() {
    ByteBuffer frame = ByteBuffer.allocateDirect(88).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(
        frame, 0, 0, 88, OrdersCanceledDecoder.TEMPLATE_ID, 5, 1, 0, 2);
    int body = 32;
    frame.putLong(body, 3);
    frame.putLong(body + 8, 4);
    frame.put(body + 16, (byte) 1);
    frame.putShort(body + 17, (short) OrdersCanceledDecoder.ORDER_BLOCK_LENGTH);
    frame.putShort(body + 19, (short) 1);
    int order = body + 21;
    frame.putLong(order, 5);
    frame.putLong(order + 8, 6);
    frame.putLong(order + 16, 7);
    frame.putLong(order + 24, 8);
    frame.put(order + 32, (byte) -2);
    frame.put(order + 33, (byte) 17);
    frame.put(order + 34, (byte) 1);
    return frame;
  }

  private static ByteBuffer orderPlacedFrame(int version) {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(
        frame, 0, 0, 128, OrderPlacedDecoder.TEMPLATE_ID, version, 22, 21, 102);
    int body = 32;
    frame.putLong(body, 5001);
    frame.putLong(body + 8, 5002);
    frame.putLong(body + 16, 5003);
    frame.putLong(body + 24, 5004);
    frame.putLong(body + 32, 5005);
    frame.putLong(body + 40, 210_000_000);
    Decimal72Codec.put(frame, body + 48, 10, -2);
    Decimal72Codec.put(frame, body + 57, 2, -2);
    Decimal72Codec.put(frame, body + 66, 8, -2);
    frame.put(body + 75, (byte) 1);
    frame.put(body + 76, (byte) 0);
    frame.put(body + 77, (byte) 0);
    frame.put(body + 78, (byte) 0);
    frame.put(body + 79, (byte) 0);
    frame.putLong(body + 80, 5006);
    frame.putShort(body + 88, (short) OrderPlacedDecoder.FILL_BLOCK_LENGTH);
    frame.putShort(body + 90, (short) 0);
    frame.putShort(body + 92, (short) OrderPlacedDecoder.LEG_BLOCK_LENGTH);
    frame.putShort(body + 94, (short) 0);
    return frame;
  }

  private static void exercise(ByteBuffer frame, RecordingHandler handler) {
    OrderEntryMessageDispatcher.dispatch(frame, 0, handler);
    sink = handler.templateId;
  }

  private static final class RecordingHandler implements OrderEntryMessageHandler {
    private int templateId;
    private int calls;

    @Override
    public void onMessage(int templateId, ByteBuffer buffer, int offset) {
      this.templateId = templateId;
      calls++;
    }
  }
}
