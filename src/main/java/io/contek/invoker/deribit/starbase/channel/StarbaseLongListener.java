package io.contek.invoker.deribit.starbase.channel;

/**
 * Allocation-free primitive event callback.
 *
 * <p>Callbacks run on the publishing I/O thread and must not block.
 */
@FunctionalInterface
public interface StarbaseLongListener {

  void onEvent(long key, long value, long timestampNanos);
}
