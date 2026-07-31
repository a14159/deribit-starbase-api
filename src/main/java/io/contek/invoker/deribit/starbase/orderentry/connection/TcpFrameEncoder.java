package io.contek.invoker.deribit.starbase.orderentry.connection;

import java.nio.ByteBuffer;

/** Writes one complete aligned TCP frame into the caller-owned reusable buffer. */
@FunctionalInterface
public interface TcpFrameEncoder {

  int encode(ByteBuffer buffer, int offset);
}
