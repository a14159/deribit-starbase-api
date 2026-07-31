package io.contek.invoker.deribit.starbase.orderentry.connection;

import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.WirePrimitives;
import io.contek.invoker.deribit.starbase.codec.orderentry.OrderEntryMessageDispatcher;
import io.contek.invoker.deribit.starbase.codec.orderentry.OrderEntryMessageHandler;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/** Fixed-capacity reusable assembler for the order-entry TCP byte stream. */
public final class TcpFrameAssembler {

  private final ByteBuffer assembly;
  private final OrderEntryMessageHandler handler;
  private int bufferedBytes;
  private boolean ended;

  public TcpFrameAssembler(int capacity, OrderEntryMessageHandler handler) {
    if (capacity < TcpHeaderCodec.ENCODED_LENGTH) {
      throw new IllegalArgumentException("capacity must hold a TCP header");
    }
    this.assembly = ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
    this.handler = Objects.requireNonNull(handler, "handler");
  }

  public void accept(ByteBuffer source, int offset, int length) {
    Objects.requireNonNull(source, "source");
    if (ended) {
      throw new IllegalStateException("TCP input already ended");
    }
    WirePrimitives.requireBounds(source, offset, length);
    int consumed = 0;
    while (consumed < length) {
      if (bufferedBytes == assembly.capacity()) {
        throw new StarbaseProtocolException("TCP frame exceeds assembler capacity");
      }
      int copied = Math.min(length - consumed, assembly.capacity() - bufferedBytes);
      for (int index = 0; index < copied; index++) {
        assembly.put(bufferedBytes + index, source.get(offset + consumed + index));
      }
      bufferedBytes += copied;
      consumed += copied;
      dispatchCompleteFrames();
    }
  }

  public void endOfInput() {
    if (ended) {
      return;
    }
    ended = true;
    if (bufferedBytes != 0) {
      throw new StarbaseProtocolException(
          "truncated TCP input with " + bufferedBytes + " buffered bytes");
    }
  }

  public int bufferedBytes() {
    return bufferedBytes;
  }

  public boolean isEnded() {
    return ended;
  }

  private void dispatchCompleteFrames() {
    while (bufferedBytes >= TcpHeaderCodec.ENCODED_LENGTH) {
      TcpHeaderCodec.validateHeader(assembly, 0);
      int messageLength = TcpHeaderCodec.messageLength(assembly, 0);
      int frameLength = WirePrimitives.align8(messageLength);
      if (frameLength > assembly.capacity()) {
        throw new StarbaseProtocolException(
            "TCP frame length " + frameLength + " exceeds assembler capacity");
      }
      if (bufferedBytes < frameLength) {
        return;
      }
      OrderEntryMessageDispatcher.dispatch(assembly, 0, handler);
      int remaining = bufferedBytes - frameLength;
      for (int index = 0; index < remaining; index++) {
        assembly.put(index, assembly.get(frameLength + index));
      }
      bufferedBytes = remaining;
    }
  }
}
