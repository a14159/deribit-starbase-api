package io.contek.invoker.deribit.starbase.orderentry.connection;

import java.nio.ByteBuffer;

/** Configured duplex transport owned exclusively by one order-entry connection. */
public interface OrderEntryDuplexTransport extends AutoCloseable {

  void open();

  /** Reads into the supplied cleared buffer, returning a positive count, zero, or -1 for EOF. */
  int read(ByteBuffer buffer);

  int write(ByteBuffer buffer, int offset, int length);

  @Override
  void close();
}
