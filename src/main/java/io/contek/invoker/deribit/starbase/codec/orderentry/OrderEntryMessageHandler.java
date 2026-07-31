package io.contek.invoker.deribit.starbase.codec.orderentry;

import java.nio.ByteBuffer;

/** Allocation-free callback invoked only after complete frame and message-layout validation. */
@FunctionalInterface
public interface OrderEntryMessageHandler {

  void onMessage(int templateId, ByteBuffer buffer, int offset);
}
