package io.contek.invoker.deribit.starbase.marketdata;

import static io.contek.invoker.deribit.starbase.common.Configuration.endpoint;
import static io.contek.invoker.deribit.starbase.common.Configuration.minimum;
import static io.contek.invoker.deribit.starbase.common.Configuration.nonBlank;
import static io.contek.invoker.deribit.starbase.common.Configuration.positive;
import static io.contek.invoker.deribit.starbase.common.Configuration.required;

import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.NanoClock;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import java.net.InetSocketAddress;
import java.time.Duration;

public record StarbaseMarketDataContext(
    ProductGroup productGroup,
    GatewaySide gatewaySide,
    String networkInterfaceName,
    InetSocketAddress incrementalGroup,
    InetSocketAddress snapshotGroup,
    InetSocketAddress retransmitEndpoint,
    int receiveBufferBytes,
    int sendBufferBytes,
    Duration retransmitTimeout,
    IoPolicy ioPolicy,
    NanoClock clock) {

  public StarbaseMarketDataContext {
    productGroup = required(productGroup, "productGroup");
    gatewaySide = required(gatewaySide, "gatewaySide");
    networkInterfaceName = nonBlank(networkInterfaceName, "networkInterfaceName");
    incrementalGroup = endpoint(incrementalGroup, "incrementalGroup");
    snapshotGroup = endpoint(snapshotGroup, "snapshotGroup");
    retransmitEndpoint = endpoint(retransmitEndpoint, "retransmitEndpoint");
    receiveBufferBytes = minimum(receiveBufferBytes, 24, "receiveBufferBytes");
    sendBufferBytes = minimum(sendBufferBytes, 24, "sendBufferBytes");
    retransmitTimeout = positive(retransmitTimeout, "retransmitTimeout");
    ioPolicy = required(ioPolicy, "ioPolicy");
    clock = required(clock, "clock");
  }
}
