package io.contek.invoker.deribit.starbase.orderentry.connection;

import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/** Serialized reusable-buffer writer that retains bytes across partial non-blocking writes. */
public final class TcpFrameWriter {

  private final ByteBuffer sendBuffer;
  private final TcpFrameTransport transport;
  private int pendingOffset;
  private int pendingBytes;
  private boolean failed;

  public TcpFrameWriter(int capacity, TcpFrameTransport transport) {
    if (capacity < TcpHeaderCodec.ENCODED_LENGTH) {
      throw new IllegalArgumentException("capacity must hold a TCP header");
    }
    this.sendBuffer = ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
    this.transport = Objects.requireNonNull(transport, "transport");
  }

  /**
   * Encodes only when no previous frame is pending, then writes until complete or backpressured.
   *
   * @return true when the encoded frame was fully written
   */
  public synchronized boolean write(TcpFrameEncoder encoder) {
    requireHealthy();
    Objects.requireNonNull(encoder, "encoder");
    if (pendingBytes != 0) {
      return false;
    }
    int encodedLength = encoder.encode(sendBuffer, 0);
    if (encodedLength < TcpHeaderCodec.ENCODED_LENGTH || encodedLength > sendBuffer.capacity()) {
      throw new StarbaseProtocolException("invalid encoded TCP frame length: " + encodedLength);
    }
    int validatedLength = TcpHeaderCodec.validateFrame(sendBuffer, 0);
    if (encodedLength != validatedLength) {
      throw new StarbaseProtocolException(
          "encoder returned " + encodedLength + " bytes for " + validatedLength + "-byte frame");
    }
    pendingOffset = 0;
    pendingBytes = encodedLength;
    return flushPending();
  }

  /** Resumes a previously partial write without invoking the encoder again. */
  public synchronized boolean flush() {
    requireHealthy();
    return flushPending();
  }

  public synchronized int pendingBytes() {
    return pendingBytes;
  }

  public synchronized boolean isFailed() {
    return failed;
  }

  private boolean flushPending() {
    while (pendingBytes != 0) {
      int written;
      try {
        written = transport.write(sendBuffer, pendingOffset, pendingBytes);
      } catch (RuntimeException failure) {
        failed = true;
        throw failure;
      }
      if (written < 0 || written > pendingBytes) {
        failed = true;
        throw new StarbaseProtocolException(
            "invalid TCP transport write result: " + written + " for " + pendingBytes);
      }
      if (written == 0) {
        return false;
      }
      pendingOffset += written;
      pendingBytes -= written;
    }
    pendingOffset = 0;
    return true;
  }

  private void requireHealthy() {
    if (failed) {
      throw new IllegalStateException("TCP frame writer has failed");
    }
  }
}
