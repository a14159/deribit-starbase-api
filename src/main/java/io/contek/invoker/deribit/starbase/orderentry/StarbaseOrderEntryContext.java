package io.contek.invoker.deribit.starbase.orderentry;

import static io.contek.invoker.deribit.starbase.common.Configuration.minimum;
import static io.contek.invoker.deribit.starbase.common.Configuration.positive;
import static io.contek.invoker.deribit.starbase.common.Configuration.required;

import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.NanoClock;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import java.net.InetSocketAddress;
import java.time.Duration;

public record StarbaseOrderEntryContext(
    InetSocketAddress endpoint,
    ProductGroup productGroup,
    GatewaySide gatewaySide,
    Duration connectTimeout,
    Duration inactivityTimeout,
    int receiveBufferBytes,
    int sendBufferBytes,
    IoPolicy ioPolicy,
    NanoClock clock) {

  public StarbaseOrderEntryContext {
    endpoint =
        io.contek.invoker.deribit.starbase.common.Configuration.endpoint(endpoint, "endpoint");
    productGroup = required(productGroup, "productGroup");
    gatewaySide = required(gatewaySide, "gatewaySide");
    connectTimeout = positive(connectTimeout, "connectTimeout");
    inactivityTimeout = positive(inactivityTimeout, "inactivityTimeout");
    receiveBufferBytes = minimum(receiveBufferBytes, 32, "receiveBufferBytes");
    sendBufferBytes = minimum(sendBufferBytes, 32, "sendBufferBytes");
    ioPolicy = required(ioPolicy, "ioPolicy");
    clock = required(clock, "clock");
  }
}
