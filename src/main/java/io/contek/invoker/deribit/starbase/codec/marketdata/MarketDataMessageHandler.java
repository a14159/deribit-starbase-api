package io.contek.invoker.deribit.starbase.codec.marketdata;

import java.nio.ByteBuffer;

/** Primitive callback invoked synchronously on the packet-dispatch thread. */
@FunctionalInterface
public interface MarketDataMessageHandler {

  /**
   * The buffer is owned and reused by the caller and is valid only for this callback.
   */
  void onMessage(
      ByteBuffer buffer, int messageOffset, int templateId, long sequenceNumber);
}
