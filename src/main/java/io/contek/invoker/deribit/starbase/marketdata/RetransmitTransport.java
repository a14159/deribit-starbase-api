package io.contek.invoker.deribit.starbase.marketdata;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Datagram exchange boundary used by the retransmit state machine and scripted tests. */
public interface RetransmitTransport {

  void send(ByteBuffer request, int length) throws IOException;

  /**
   * Receives one response into the supplied reusable buffer.
   *
   * @return response length, or zero on timeout
   */
  int receive(ByteBuffer response, long timeoutNanos) throws IOException;
}
