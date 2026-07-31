package io.contek.invoker.deribit.starbase.orderentry.state;

/** Synchronous I/O-thread callback for one newly accepted fill. */
@FunctionalInterface
public interface FillListener {

  void onFill(
      long sessionId,
      long matchId,
      long orderId,
      long fillQuantity,
      long remainingQuantity);
}
