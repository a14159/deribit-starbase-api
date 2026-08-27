package io.contek.invoker.deribit.starbase.orderentry.command;

import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import java.util.Objects;

/** Deterministic one-endpoint A/B router with bounded primitive origin-session records. */
public final class OrderSessionRouter {

  private final OrderRouteEndpoint[] sideA = new OrderRouteEndpoint[ProductGroup.values().length];
  private final OrderRouteEndpoint[] sideB = new OrderRouteEndpoint[ProductGroup.values().length];
  private final long[] clientOrderIds;
  private final long[] originSessionIds;
  private final byte[] occupied;
  private int size;

  public OrderSessionRouter(
      int originCapacity, OrderRouteEndpoint... endpoints) {
    if (originCapacity < 1) {
      throw new IllegalArgumentException("originCapacity must be positive");
    }
    Objects.requireNonNull(endpoints, "endpoints");
    clientOrderIds = new long[originCapacity];
    originSessionIds = new long[originCapacity];
    occupied = new byte[originCapacity];
    for (OrderRouteEndpoint endpoint : endpoints) {
      register(Objects.requireNonNull(endpoint, "endpoint"));
    }
  }

  public synchronized long routeNewOrder(ProductGroup productGroup, long clientOrderId) {
    Objects.requireNonNull(productGroup, "productGroup");
    if (clientOrderId == Long.MIN_VALUE) {
      throw new IllegalArgumentException("clientOrderId is the SBE null value");
    }
    if (find(clientOrderId) >= 0) {
      throw new IllegalStateException("client order already has an origin session");
    }
    int slot = emptySlot();
    if (slot < 0) {
      throw new IllegalStateException("origin-session capacity exhausted");
    }
    int group = productGroup.ordinal();
    OrderRouteEndpoint endpoint = ready(sideA[group]) ? sideA[group] : sideB[group];
    if (!ready(endpoint)) {
      throw new IllegalStateException("no ready order-entry endpoint for " + productGroup);
    }
    long correlationId = endpoint.submitNewOrder(clientOrderId);
    clientOrderIds[slot] = clientOrderId;
    originSessionIds[slot] = endpoint.sessionId();
    occupied[slot] = 1;
    size++;
    return correlationId;
  }

  public synchronized boolean hasReadyEndpoint(ProductGroup productGroup) {
    Objects.requireNonNull(productGroup, "productGroup");
    int group = productGroup.ordinal();
    return ready(sideA[group]) || ready(sideB[group]);
  }

  public synchronized long originSessionId(long clientOrderId) {
    int slot = find(clientOrderId);
    if (slot < 0) {
      throw new IllegalArgumentException("unknown clientOrderId: " + clientOrderId);
    }
    return originSessionIds[slot];
  }

  /** Releases origin metadata only after the order can no longer emit lifecycle events. */
  public synchronized boolean releaseOrigin(long clientOrderId) {
    int slot = find(clientOrderId);
    if (slot < 0) {
      return false;
    }
    occupied[slot] = 0;
    clientOrderIds[slot] = 0;
    originSessionIds[slot] = 0;
    size--;
    return true;
  }

  public synchronized int size() {
    return size;
  }

  private void register(OrderRouteEndpoint endpoint) {
    ProductGroup group = Objects.requireNonNull(endpoint.productGroup(), "productGroup");
    GatewaySide side = Objects.requireNonNull(endpoint.gatewaySide(), "gatewaySide");
    if (endpoint.sessionId() == Long.MIN_VALUE) {
      throw new IllegalArgumentException("sessionId is null");
    }
    OrderRouteEndpoint[] routes = side == GatewaySide.A ? sideA : sideB;
    int index = group.ordinal();
    if (routes[index] != null) {
      throw new IllegalArgumentException("duplicate endpoint for " + group + '/' + side);
    }
    routes[index] = endpoint;
  }

  private int find(long clientOrderId) {
    for (int slot = 0; slot < occupied.length; slot++) {
      if (occupied[slot] != 0 && clientOrderIds[slot] == clientOrderId) {
        return slot;
      }
    }
    return -1;
  }

  private int emptySlot() {
    for (int slot = 0; slot < occupied.length; slot++) {
      if (occupied[slot] == 0) {
        return slot;
      }
    }
    return -1;
  }

  private static boolean ready(OrderRouteEndpoint endpoint) {
    return endpoint != null && endpoint.isReady();
  }
}
