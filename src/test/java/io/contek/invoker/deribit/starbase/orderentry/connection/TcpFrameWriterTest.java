package io.contek.invoker.deribit.starbase.orderentry.connection;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.orderentry.LogoutCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import io.contek.invoker.deribit.starbase.codec.orderentry.HeartbeatCodec;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class TcpFrameWriterTest {

  private static volatile int sink;

  public void testPartialWriteRetainsUnsentBytesAndResumesWithoutReencoding() {
    ScriptedTransport transport = new ScriptedTransport();
    TcpFrameWriter writer = new TcpFrameWriter(256, transport);
    CountingHeartbeatEncoder encoder = new CountingHeartbeatEncoder();

    assertFalse(writer.write(encoder));
    assertEquals(27, writer.pendingBytes());
    assertEquals(1, encoder.calls);

    transport.writable = true;
    assertTrue(writer.flush());
    assertEquals(0, writer.pendingBytes());
    assertEquals(1, encoder.calls);
    assertEquals(HeartbeatCodec.MESSAGE_LENGTH, transport.totalWritten);
  }

  public void testBackpressureRejectsASecondFrameWithoutReencodingAndPaddingIsZero() {
    ScriptedTransport transport = new ScriptedTransport();
    TcpFrameWriter writer = new TcpFrameWriter(256, transport);
    CountingHeartbeatEncoder first = new CountingHeartbeatEncoder();
    CountingHeartbeatEncoder second = new CountingHeartbeatEncoder();
    assertFalse(writer.write(first));
    assertFalse(writer.write(second));
    assertEquals(0, second.calls);

    transport.writable = true;
    assertTrue(writer.flush());
    PaddingTransport padding = new PaddingTransport();
    TcpFrameWriter paddingWriter = new TcpFrameWriter(64, padding);
    assertTrue(
        paddingWriter.write(
            (buffer, offset) -> LogoutCodec.encode(buffer, offset, new char[] {'o', 'k'}, 2, 1, 3)));
    assertEquals(40, padding.length);
    assertTrue(padding.paddingWasZero);
  }

  public void testConcurrentCallersAreSerializedAroundTheSingleReusableBuffer() throws Exception {
    BlockingTransport transport = new BlockingTransport();
    TcpFrameWriter writer = new TcpFrameWriter(64, transport);
    CountingHeartbeatEncoder first = new CountingHeartbeatEncoder();
    CountingHeartbeatEncoder second = new CountingHeartbeatEncoder();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread firstThread = new Thread(() -> write(writer, first, failure));
    Thread secondThread = new Thread(() -> write(writer, second, failure));

    firstThread.start();
    assertTrue(transport.entered.await(5, TimeUnit.SECONDS));
    secondThread.start();
    assertEquals(0, second.calls);
    transport.release.countDown();
    firstThread.join(5_000);
    secondThread.join(5_000);

    assertEquals(null, failure.get());
    assertEquals(1, first.calls);
    assertEquals(1, second.calls);
    assertEquals(2, transport.frames);
  }

  public void testInvalidTransportResultAndTransportFailurePoisonTheWriter() {
    TcpFrameWriter invalid = new TcpFrameWriter(64, (buffer, offset, length) -> length + 1);
    assertThrows(
        StarbaseProtocolException.class,
        () -> invalid.write(new CountingHeartbeatEncoder()));
    assertTrue(invalid.isFailed());
    assertThrows(IllegalStateException.class, invalid::flush);

    TcpFrameWriter failed =
        new TcpFrameWriter(
            64,
            (buffer, offset, length) -> {
              throw new IllegalStateException("transport failed");
            });
    assertThrows(
        IllegalStateException.class,
        () -> failed.write(new CountingHeartbeatEncoder()));
    assertTrue(failed.isFailed());
  }

  public void testWarmedEncodeAndFullWriteAllocateNothing() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    FullTransport transport = new FullTransport();
    TcpFrameWriter writer = new TcpFrameWriter(64, transport);
    CountingHeartbeatEncoder encoder = new CountingHeartbeatEncoder();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      exercise(writer, encoder);
    }
    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(writer, encoder);
    }
    assertEquals(
        0L,
        bean.getThreadAllocatedBytes(threadId) - before,
        "TCP encode/write hot path allocated bytes");
  }

  private static void write(
      TcpFrameWriter writer, TcpFrameEncoder encoder, AtomicReference<Throwable> failure) {
    try {
      if (!writer.write(encoder)) {
        failure.compareAndSet(null, new AssertionError("unexpected backpressure"));
      }
    } catch (Throwable throwable) {
      failure.compareAndSet(null, throwable);
    }
  }

  private static void exercise(TcpFrameWriter writer, CountingHeartbeatEncoder encoder) {
    if (!writer.write(encoder)) {
      throw new AssertionError("unexpected backpressure");
    }
    sink = encoder.calls;
  }

  private static final class CountingHeartbeatEncoder implements TcpFrameEncoder {
    private int calls;

    @Override
    public int encode(ByteBuffer buffer, int offset) {
      calls++;
      return HeartbeatCodec.encode(buffer, offset, 17, 1, 0, 2);
    }
  }

  private static final class ScriptedTransport implements TcpFrameTransport {
    private int totalWritten;
    private boolean writable;

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      if (totalWritten == 0) {
        totalWritten = 13;
        return 13;
      }
      if (!writable) {
        return 0;
      }
      totalWritten += length;
      return length;
    }
  }

  private static final class PaddingTransport implements TcpFrameTransport {
    private int length;
    private boolean paddingWasZero;

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      this.length = length;
      paddingWasZero = true;
      for (int index = 35; index < 40; index++) {
        paddingWasZero &= buffer.get(offset + index) == 0;
      }
      return length;
    }
  }

  private static final class BlockingTransport implements TcpFrameTransport {
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private int frames;

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      if (frames == 0) {
        entered.countDown();
        try {
          if (!release.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("release timeout");
          }
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(interrupted);
        }
      }
      frames++;
      return length;
    }
  }

  private static final class FullTransport implements TcpFrameTransport {
    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      return length;
    }
  }
}
