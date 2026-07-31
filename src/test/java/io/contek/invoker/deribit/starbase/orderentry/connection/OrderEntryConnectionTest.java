package io.contek.invoker.deribit.starbase.orderentry.connection;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.codec.orderentry.HeartbeatCodec;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class OrderEntryConnectionTest {

  public void testExplicitStartOwnsOneReadLoopAndIdleConsumersNeverCloseIt() throws Exception {
    BlockingDuplexTransport transport = new BlockingDuplexTransport();
    OrderEntryConnection connection =
        new OrderEntryConnection(transport, 256, 256, (templateId, buffer, offset) -> {});

    connection.start();
    connection.start();
    assertTrue(transport.readEntered.await(5, TimeUnit.SECONDS));
    assertTrue(connection.isRunning());
    assertEquals(1, transport.openCalls);

    Thread.sleep(20);
    assertFalse(transport.closed);
    connection.close();
    connection.close();
    assertTrue(connection.isClosed());
    assertEquals(1, transport.closeCalls);
  }

  public void testOwnedReadLoopDispatchesFramesAndUnexpectedEofFailsUnavailable() throws Exception {
    OneFrameTransport transport = new OneFrameTransport(false);
    CountDownLatch dispatched = new CountDownLatch(1);
    OrderEntryConnection connection =
        new OrderEntryConnection(
            transport,
            256,
            256,
            (templateId, buffer, offset) -> {
              assertEquals(HeartbeatCodec.TEMPLATE_ID, templateId);
              assertEquals(17, HeartbeatCodec.correlationId(buffer, offset));
              dispatched.countDown();
            });
    connection.start();

    assertTrue(dispatched.await(5, TimeUnit.SECONDS));
    awaitFailure(connection);
    assertTrue(connection.isFailed());
    assertEquals(1, transport.closeCalls);
    assertThrows(
        IllegalStateException.class,
        () -> connection.write((buffer, offset) -> HeartbeatCodec.encode(buffer, offset, 1, 1, 0, 2)));
    connection.close();
    assertEquals(1, transport.closeCalls);
  }

  public void testCorruptReadAndOpenFailureCloseExactlyOnceAndExposeCause() throws Exception {
    OneFrameTransport corrupt = new OneFrameTransport(true);
    OrderEntryConnection corruptConnection =
        new OrderEntryConnection(corrupt, 256, 256, (templateId, buffer, offset) -> {});
    corruptConnection.start();
    awaitFailure(corruptConnection);
    assertTrue(corruptConnection.failure() != null);
    assertEquals(1, corrupt.closeCalls);

    FailingOpenTransport opening = new FailingOpenTransport();
    OrderEntryConnection openFailure =
        new OrderEntryConnection(opening, 64, 64, (templateId, buffer, offset) -> {});
    assertThrows(IllegalStateException.class, openFailure::start);
    assertTrue(openFailure.isFailed());
    assertEquals(1, opening.closeCalls);
    openFailure.close();
    assertEquals(1, opening.closeCalls);
    assertThrows(IllegalStateException.class, openFailure::start);
  }

  public void testOutboundWritesAreGatedByExplicitRunningLifetime() throws Exception {
    BlockingDuplexTransport transport = new BlockingDuplexTransport();
    OrderEntryConnection connection =
        new OrderEntryConnection(transport, 64, 64, (templateId, buffer, offset) -> {});
    assertThrows(
        IllegalStateException.class,
        () -> connection.write((buffer, offset) -> HeartbeatCodec.encode(buffer, offset, 1, 1, 0, 2)));
    connection.start();
    assertTrue(transport.readEntered.await(5, TimeUnit.SECONDS));
    assertTrue(
        connection.write(
            (buffer, offset) -> HeartbeatCodec.encode(buffer, offset, 1, 1, 0, 2)));
    connection.close();
    assertThrows(IllegalStateException.class, connection::flush);
  }

  private static void awaitFailure(OrderEntryConnection connection) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!connection.isFailed() && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertTrue(connection.isFailed(), "connection did not enter failed state");
  }

  private static final class BlockingDuplexTransport implements OrderEntryDuplexTransport {
    private final CountDownLatch readEntered = new CountDownLatch(1);
    private final CountDownLatch closedSignal = new CountDownLatch(1);
    private int openCalls;
    private int closeCalls;
    private volatile boolean closed;

    @Override
    public void open() {
      openCalls++;
    }

    @Override
    public int read(ByteBuffer buffer) {
      readEntered.countDown();
      try {
        closedSignal.await();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      return -1;
    }

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      return length;
    }

    @Override
    public void close() {
      closeCalls++;
      closed = true;
      closedSignal.countDown();
    }
  }

  private static final class OneFrameTransport implements OrderEntryDuplexTransport {
    private final boolean corrupt;
    private int reads;
    private int closeCalls;

    private OneFrameTransport(boolean corrupt) {
      this.corrupt = corrupt;
    }

    @Override
    public void open() {}

    @Override
    public int read(ByteBuffer buffer) {
      if (reads++ != 0) {
        return -1;
      }
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      HeartbeatCodec.encode(buffer, 0, 17, 1, 0, 2);
      if (corrupt) {
        buffer.put(0, (byte) 0);
      }
      return HeartbeatCodec.MESSAGE_LENGTH;
    }

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      return length;
    }

    @Override
    public void close() {
      closeCalls++;
    }
  }

  private static final class FailingOpenTransport implements OrderEntryDuplexTransport {
    private int closeCalls;

    @Override
    public void open() {
      throw new IllegalStateException("open failed");
    }

    @Override
    public int read(ByteBuffer buffer) {
      return -1;
    }

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      return length;
    }

    @Override
    public void close() {
      closeCalls++;
    }
  }
}
