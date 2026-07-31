package io.contek.invoker.deribit.starbase.orderentry.command;

import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.ProductGroup;

/** One configured product-group/gateway-side order submission endpoint. */
public interface OrderRouteEndpoint {

  long sessionId();

  ProductGroup productGroup();

  GatewaySide gatewaySide();

  boolean isReady();

  long submitNewOrder(long clientOrderId);
}
