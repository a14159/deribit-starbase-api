package io.contek.invoker.deribit.starbase.orderentry.connection;

import java.nio.ByteBuffer;

/** Non-blocking transport write boundary; zero indicates temporary backpressure. */
@FunctionalInterface
public interface TcpFrameTransport {

  int write(ByteBuffer buffer, int offset, int length);
}
