package io.contek.invoker.deribit.starbase.orderentry.command;

/** Allocation-free trading readiness view used by the outbound command facade. */
@FunctionalInterface
public interface OrderCommandReadiness {

  boolean isReady();
}
