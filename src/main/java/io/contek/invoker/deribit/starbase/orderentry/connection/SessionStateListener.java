package io.contek.invoker.deribit.starbase.orderentry.connection;

/** Stable primitive callback for order-entry connection/readiness transitions. */
@FunctionalInterface
public interface SessionStateListener {

  void onStateChanged(int state);
}
