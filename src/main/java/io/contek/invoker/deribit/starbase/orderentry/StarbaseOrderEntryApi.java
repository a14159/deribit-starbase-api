package io.contek.invoker.deribit.starbase.orderentry;

import io.contek.invoker.deribit.starbase.channel.StarbaseLongChannel;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.GapFillDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.AmendOrderResponseDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.AmendOrderRejectDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.CancelOrderRejectDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.CancelOrderResponseDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.HeartbeatCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.LoggedOutCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.LogonConfirmationCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.MassCancelResponseDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.MassCancelRejectDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.NewOrderRejectDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.NewOrderRequestEncoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.NewOrderResponseDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.OrderEntryMessageHandler;
import io.contek.invoker.deribit.starbase.codec.orderentry.OrderFilledDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.OrderPlacedDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.OrdersCanceledDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.ResendRequestCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.SessionRejectDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.TestRequestCodec;
import io.contek.invoker.deribit.starbase.common.AbstractStarbaseApi;
import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.common.StarbaseCredentials;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import io.contek.invoker.deribit.starbase.orderentry.command.OrderCommandFacade;
import io.contek.invoker.deribit.starbase.orderentry.command.OrderRouteEndpoint;
import io.contek.invoker.deribit.starbase.orderentry.command.OrderSessionRouter;
import io.contek.invoker.deribit.starbase.orderentry.connection.AuthenticationStateMachine;
import io.contek.invoker.deribit.starbase.orderentry.connection.OrderEntryConnection;
import io.contek.invoker.deribit.starbase.orderentry.connection.OrderEntryDuplexTransport;
import io.contek.invoker.deribit.starbase.orderentry.connection.ReconnectReadiness;
import io.contek.invoker.deribit.starbase.orderentry.connection.SessionLiveness;
import io.contek.invoker.deribit.starbase.orderentry.connection.SessionSequenceState;
import io.contek.invoker.deribit.starbase.orderentry.connection.SocketOrderEntryTransport;
import io.contek.invoker.deribit.starbase.orderentry.connection.TcpFrameEncoder;
import io.contek.invoker.deribit.starbase.orderentry.state.CorrelationTable;
import io.contek.invoker.deribit.starbase.orderentry.state.ClientOrderIdMap;
import io.contek.invoker.deribit.starbase.orderentry.state.LocalOrderStateStore;
import io.contek.invoker.deribit.starbase.orderentry.state.OrderFillProcessor;
import io.contek.invoker.deribit.starbase.orderentry.state.OrderStateReconciliation;
import io.contek.invoker.deribit.starbase.rest.OpenOrderRecoveryCache;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/** Public paired order-entry lifecycle with exact state and REST recovery gates. */
public final class StarbaseOrderEntryApi extends AbstractStarbaseApi {

  private static final int DEFAULT_ORDER_CAPACITY = 4_096;
  private static final int DEFAULT_CORRELATION_CAPACITY = 4_096;
  private static final int DEFAULT_MATCH_CAPACITY = 16_384;
  private static final long SIDE_A_SESSION_ID = 1;
  private static final long SIDE_B_SESSION_ID = 2;
  private static final long SIDE_B_INITIAL_CORRELATION_ID = 1L << 62;
  private static final Duration INITIAL_RECONNECT_BACKOFF = Duration.ofMillis(100);
  private static final Duration MAXIMUM_RECONNECT_BACKOFF = Duration.ofSeconds(5);
  private static final Duration MAXIMUM_SNAPSHOT_AGE = Duration.ofMinutes(2);
  private static final ClientOrderIdMap CLIENT_ORDER_IDS = new ClientOrderIdMap();

  private final StarbaseOrderEntryContext context;
  private final StarbaseOrderEntryContext secondaryContext;
  private final StarbaseCredentials credentials;
  private final StarbaseCredentials secondaryCredentials;
  private final OpenOrderRecoveryCache recoveryCache;
  private final Function<StarbaseOrderEntryContext, OrderEntryDuplexTransport> transportFactory;
  private final StarbaseLongChannel orderEvents = new StarbaseLongChannel();
  private final StarbaseLongChannel fills = new StarbaseLongChannel();
  private final StarbaseLongChannel sessionEvents = new StarbaseLongChannel();
  private final Object commandLock = new Object();
  private final boolean assembled;
  private final LocalOrderStateStore orders;
  private final OrderFillProcessor fillProcessor;
  private final Session sideA;
  private final Session sideB;
  private final OrderSessionRouter router;

  private volatile boolean referenceDataReady;
  private volatile boolean globalRecoverySafe = true;
  private volatile long recoveryEpoch;
  private long fillResetEpoch = -1;
  private volatile boolean supervisorStop;
  private Thread supervisor;

  private int stagedKind;
  private long stagedClientOrderId;
  private long stagedInstrumentId;
  private long stagedPriceMantissa;
  private long stagedQuantityMantissa;
  private int stagedQuantityExponent;
  private boolean stagedShowQuantityNull;
  private long stagedShowQuantityMantissa;
  private long stagedSelfMatchPreventionId;
  private int stagedSide;
  private int stagedTimeInForce;
  private int stagedFlags;
  private int stagedSelfTradingMode;

  /** Legacy one-context facade retained for source compatibility and intentionally non-ready. */
  public StarbaseOrderEntryApi(
      StarbaseOrderEntryContext context, StarbaseCredentials sourceCredentials) {
    this.context = Objects.requireNonNull(context, "context");
    Objects.requireNonNull(sourceCredentials, "sourceCredentials");
    credentials = copyCredentials(sourceCredentials);
    secondaryContext = null;
    secondaryCredentials = null;
    recoveryCache = null;
    transportFactory = null;
    assembled = false;
    orders = null;
    fillProcessor = null;
    sideA = null;
    sideB = null;
    router = null;
  }

  /** Constructs the required A/B pair with separate gateway credentials and exact recovery. */
  public StarbaseOrderEntryApi(
      StarbaseOrderEntryContext firstContext,
      StarbaseCredentials firstCredentials,
      StarbaseOrderEntryContext secondContext,
      StarbaseCredentials secondCredentials,
      OpenOrderRecoveryCache recoveryCache) {
    this(
        firstContext,
        firstCredentials,
        secondContext,
        secondCredentials,
        recoveryCache,
        SocketOrderEntryTransport::new);
  }

  StarbaseOrderEntryApi(
      StarbaseOrderEntryContext firstContext,
      StarbaseCredentials firstCredentials,
      StarbaseOrderEntryContext secondContext,
      StarbaseCredentials secondCredentials,
      OpenOrderRecoveryCache recoveryCache,
      Function<StarbaseOrderEntryContext, OrderEntryDuplexTransport> transportFactory) {
    StarbaseOrderEntryContext[] pair = validatePair(firstContext, secondContext);
    boolean firstIsSideA = firstContext.gatewaySide() == GatewaySide.A;
    context = pair[0];
    secondaryContext = pair[1];
    Objects.requireNonNull(firstCredentials, "firstCredentials");
    Objects.requireNonNull(secondCredentials, "secondCredentials");
    credentials = copyCredentials(firstIsSideA ? firstCredentials : secondCredentials);
    secondaryCredentials =
        copyCredentials(firstIsSideA ? secondCredentials : firstCredentials);
    this.recoveryCache = Objects.requireNonNull(recoveryCache, "recoveryCache");
    this.transportFactory = Objects.requireNonNull(transportFactory, "transportFactory");
    assembled = true;
    orders = new LocalOrderStateStore(DEFAULT_ORDER_CAPACITY);
    fillProcessor =
        new OrderFillProcessor(
            orders,
            DEFAULT_MATCH_CAPACITY,
            (sessionId, matchId, orderId, fillQuantity, remainingQuantity) ->
                fills.publish(orderId, matchId, context.clock().nanoTime()));
    sideA =
        new Session(
            SIDE_A_SESSION_ID,
            context,
            credentials,
            1,
            DEFAULT_CORRELATION_CAPACITY);
    sideB =
        new Session(
            SIDE_B_SESSION_ID,
            secondaryContext,
            secondaryCredentials,
            SIDE_B_INITIAL_CORRELATION_ID,
            DEFAULT_CORRELATION_CAPACITY);
    router = new OrderSessionRouter(DEFAULT_ORDER_CAPACITY, sideA, sideB);
  }

  public StarbaseOrderEntryContext context() {
    return context;
  }

  public StarbaseOrderEntryContext secondaryContext() {
    return secondaryContext;
  }

  public boolean isAuthenticated() {
    return assembled && (sideA.isAuthenticated() || sideB.isAuthenticated());
  }

  public boolean isAuthenticated(GatewaySide side) {
    Objects.requireNonNull(side, "side");
    return assembled && session(side).isAuthenticated();
  }

  public boolean isConnected(GatewaySide side) {
    Objects.requireNonNull(side, "side");
    return assembled && session(side).readiness.isConnected();
  }

  public int sessionState(GatewaySide side) {
    Objects.requireNonNull(side, "side");
    return assembled ? session(side).readiness.state() : ReconnectReadiness.STATE_NEW;
  }

  public int reconciliationResult(GatewaySide side) {
    Objects.requireNonNull(side, "side");
    return assembled
        ? session(side).reconciliation.lastResult()
        : OrderStateReconciliation.RESULT_NONE;
  }

  public boolean isReady() {
    return assembled
        && globalRecoverySafe
        && globallySequenceSafe()
        && (sideA.readiness.isReady() || sideB.readiness.isReady());
  }

  public boolean isReady(GatewaySide side) {
    Objects.requireNonNull(side, "side");
    return assembled
        && globalRecoverySafe
        && globallySequenceSafe()
        && session(side).readiness.isReady();
  }

  public void setReferenceDataReady(boolean ready) {
    referenceDataReady = ready;
    if (assembled) {
      sideA.setReferenceDataReady(ready);
      sideB.setReferenceDataReady(ready);
    }
  }

  public boolean isReferenceDataReady() {
    return referenceDataReady;
  }

  public long newLimit(
      String clientOrderId,
      long instrumentId,
      long priceMantissa,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      long selfMatchPreventionId,
      int side,
      int timeInForce,
      int flags,
      int selfTradingMode) {
    return newLimit(
        CLIENT_ORDER_IDS.map(clientOrderId),
        instrumentId,
        priceMantissa,
        quantityMantissa,
        quantityExponent,
        showQuantityNull,
        showQuantityMantissa,
        selfMatchPreventionId,
        side,
        timeInForce,
        flags,
        selfTradingMode);
  }

  public long newLimit(
      long clientOrderId,
      long instrumentId,
      long priceMantissa,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      long selfMatchPreventionId,
      int side,
      int timeInForce,
      int flags,
      int selfTradingMode) {
    requireAssembled();
    NewOrderRequestEncoder.validateLimit(
        clientOrderId,
        instrumentId,
        priceMantissa,
        quantityMantissa,
        quantityExponent,
        showQuantityNull,
        showQuantityMantissa,
        side,
        timeInForce,
        flags,
        selfTradingMode);
    synchronized (commandLock) {
      requireNewOrderRoute();
      registerPending(
          clientOrderId,
          instrumentId,
          side,
          priceMantissa,
          quantityMantissa,
          quantityExponent);
      stageNew(
          1,
          clientOrderId,
          instrumentId,
          priceMantissa,
          quantityMantissa,
          quantityExponent,
          showQuantityNull,
          showQuantityMantissa,
          selfMatchPreventionId,
          side,
          timeInForce,
          flags,
          selfTradingMode);
      try {
        return router.routeNewOrder(context.productGroup(), clientOrderId);
      } finally {
        stagedKind = 0;
      }
    }
  }

  public long newMarket(
      String clientOrderId,
      long instrumentId,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      long selfMatchPreventionId,
      int side,
      int timeInForce,
      int flags,
      int selfTradingMode) {
    return newMarket(
        CLIENT_ORDER_IDS.map(clientOrderId),
        instrumentId,
        quantityMantissa,
        quantityExponent,
        showQuantityNull,
        showQuantityMantissa,
        selfMatchPreventionId,
        side,
        timeInForce,
        flags,
        selfTradingMode);
  }

  public long newMarket(
      long clientOrderId,
      long instrumentId,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      long selfMatchPreventionId,
      int side,
      int timeInForce,
      int flags,
      int selfTradingMode) {
    requireAssembled();
    NewOrderRequestEncoder.validateMarket(
        clientOrderId,
        instrumentId,
        quantityMantissa,
        quantityExponent,
        showQuantityNull,
        showQuantityMantissa,
        side,
        timeInForce,
        flags,
        selfTradingMode);
    synchronized (commandLock) {
      requireNewOrderRoute();
      registerPending(
          clientOrderId,
          instrumentId,
          side,
          Long.MIN_VALUE,
          quantityMantissa,
          quantityExponent);
      stageNew(
          2,
          clientOrderId,
          instrumentId,
          Long.MIN_VALUE,
          quantityMantissa,
          quantityExponent,
          showQuantityNull,
          showQuantityMantissa,
          selfMatchPreventionId,
          side,
          timeInForce,
          flags,
          selfTradingMode);
      try {
        return router.routeNewOrder(context.productGroup(), clientOrderId);
      } finally {
        stagedKind = 0;
      }
    }
  }

  public long amend(
      String clientOrderId,
      long instrumentId,
      long priceMantissa,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      int flags) {
    return amend(
        CLIENT_ORDER_IDS.map(clientOrderId),
        instrumentId,
        priceMantissa,
        quantityMantissa,
        quantityExponent,
        showQuantityNull,
        showQuantityMantissa,
        flags);
  }

  public long amend(
      long clientOrderId,
      long instrumentId,
      long priceMantissa,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      int flags) {
    requireAssembled();
    synchronized (commandLock) {
      Session origin = originSession(clientOrderId, instrumentId);
      origin.requireCommandReady();
      return origin.commands.amend(
          clientOrderId,
          instrumentId,
          priceMantissa,
          quantityMantissa,
          quantityExponent,
          showQuantityNull,
          showQuantityMantissa,
          flags);
    }
  }

  public long cancel(long clientOrderId, long instrumentId) {
    requireAssembled();
    synchronized (commandLock) {
      Session origin = originSession(clientOrderId, instrumentId);
      origin.requireCommandReady();
      return origin.commands.cancel(clientOrderId, instrumentId);
    }
  }

  public long cancel(String clientOrderId, long instrumentId) {
    return cancel(CLIENT_ORDER_IDS.map(clientOrderId), instrumentId);
  }

  public long cancelByOrderId(long orderId) {
    requireAssembled();
    synchronized (commandLock) {
      long clientOrderId = orders.clientOrderId(orderId);
      long instrumentId = orders.instrumentId(orderId);
      Session origin = originSession(clientOrderId, instrumentId);
      origin.requireCommandReady();
      return origin.commands.cancelByOrderId(orderId, clientOrderId, instrumentId);
    }
  }

  public long massCancel(
      long currencyPairId, long instrumentId, int productType, int side) {
    requireAssembled();
    synchronized (commandLock) {
      Session endpoint = sideA.isReady() ? sideA : sideB;
      endpoint.requireCommandReady();
      return endpoint.commands.massCancel(currencyPairId, instrumentId, productType, side);
    }
  }

  public int correlationState(long correlationId) {
    requireAssembled();
    int state = sideA.correlations.state(correlationId);
    return state == CorrelationTable.STATE_EMPTY
        ? sideB.correlations.state(correlationId)
        : state;
  }

  public int correlationResultCode(long correlationId) {
    requireAssembled();
    CorrelationTable table = correlationTable(correlationId);
    return table.resultCode(correlationId);
  }

  public int orderStateByClientOrderId(long clientOrderId) {
    requireAssembled();
    return orders.stateByClientOrderId(clientOrderId);
  }

  public int orderStateByOrderId(long orderId) {
    requireAssembled();
    return orders.stateByOrderId(orderId);
  }

  public long remainingQuantity(long orderId) {
    requireAssembled();
    return orders.remainingQuantity(orderId);
  }

  public StarbaseLongChannel getOrderEventsChannel() {
    return orderEvents;
  }

  public StarbaseLongChannel getFillsChannel() {
    return fills;
  }

  public StarbaseLongChannel getSessionEventsChannel() {
    return sessionEvents;
  }

  @Override
  protected void onStart() {
    if (!assembled) {
      return;
    }
    sideA.start();
    sideB.start();
    supervisorStop = false;
    supervisor = new Thread(this::runSupervisor, "starbase-order-entry-supervisor");
    supervisor.setDaemon(true);
    supervisor.start();
  }

  @Override
  protected void onClose() {
    supervisorStop = true;
    Thread thread = supervisor;
    if (thread != null) {
      LockSupport.unpark(thread);
    }
    if (assembled) {
      sideA.close();
      sideB.close();
    }
    if (thread != null && thread != Thread.currentThread()) {
      try {
        thread.join(5_000);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
    credentials.close();
    if (secondaryCredentials != null) {
      secondaryCredentials.close();
    }
  }

  private void runSupervisor() {
    while (!supervisorStop) {
      sideA.poll();
      sideB.poll();
      if (context.ioPolicy() == IoPolicy.SPIN
          || secondaryContext.ioPolicy() == IoPolicy.SPIN) {
        Thread.onSpinWait();
      } else {
        LockSupport.parkNanos(100_000);
      }
    }
  }

  private void onApplicationMessage(
      Session session, int templateId, ByteBuffer buffer, int offset) {
    synchronized (commandLock) {
      switch (templateId) {
        case NewOrderResponseDecoder.TEMPLATE_ID -> onNewOrderResponse(session, buffer, offset);
        case NewOrderRejectDecoder.TEMPLATE_ID -> onNewOrderReject(session, buffer, offset);
        case OrderFilledDecoder.TEMPLATE_ID -> onOrderFilled(session, buffer, offset);
        case AmendOrderResponseDecoder.TEMPLATE_ID -> onAmendOrderResponse(session, buffer, offset);
        case AmendOrderRejectDecoder.TEMPLATE_ID -> onAmendOrderReject(session, buffer, offset);
        case CancelOrderResponseDecoder.TEMPLATE_ID -> onCancelOrderResponse(session, buffer, offset);
        case CancelOrderRejectDecoder.TEMPLATE_ID -> onCancelOrderReject(session, buffer, offset);
        case MassCancelResponseDecoder.TEMPLATE_ID -> onMassCancelResponse(session, buffer, offset);
        case MassCancelRejectDecoder.TEMPLATE_ID -> onMassCancelReject(session, buffer, offset);
        case OrderPlacedDecoder.TEMPLATE_ID -> onOrderPlaced(session, buffer, offset);
        case OrdersCanceledDecoder.TEMPLATE_ID -> onOrdersCanceled(session, buffer, offset);
        default ->
            throw new StarbaseProtocolException(
                "assembled order-entry state does not support template " + templateId);
      }
    }
  }

  private void onNewOrderResponse(Session session, ByteBuffer buffer, int offset) {
    long correlationId = NewOrderResponseDecoder.correlationId(buffer, offset);
    long clientOrderId = NewOrderResponseDecoder.clientOrderId(buffer, offset);
    requireCorrelation(session, correlationId, OrderCommandFacade.COMMAND_NEW, clientOrderId);
    long instrumentId = NewOrderResponseDecoder.instrumentId(buffer, offset);
    if (orders.instrumentIdByClientOrderId(clientOrderId) != instrumentId) {
      throw new StarbaseProtocolException("new-order response instrument mismatch");
    }
    long quantity = NewOrderResponseDecoder.quantityMantissa(buffer, offset);
    long totalFilled = NewOrderResponseDecoder.totalFilledMantissa(buffer, offset);
    int exponent = NewOrderResponseDecoder.quantityExponent(buffer, offset);
    if (orders.quantityExponentByClientOrderId(clientOrderId) != exponent
        || NewOrderResponseDecoder.totalFilledExponent(buffer, offset) != exponent) {
      throw new StarbaseProtocolException("new-order response quantity exponent mismatch");
    }
    int status = NewOrderResponseDecoder.status(buffer, offset);
    long orderId = NewOrderResponseDecoder.orderId(buffer, offset);
    long eventSequence = TcpHeaderCodec.sequenceNumber(buffer, offset);
    boolean recorded =
        status == 4
            ? orders.queue(session.sessionId, eventSequence, clientOrderId, orderId, quantity)
            : orders.place(session.sessionId, eventSequence, clientOrderId, orderId, quantity);
    if (!recorded) {
      throw new StarbaseProtocolException("new-order response does not match pending state");
    }
    long fillTotal = 0;
    int fillCount = NewOrderResponseDecoder.fillCount(buffer, offset);
    for (int index = 0; index < fillCount; index++) {
      if (NewOrderResponseDecoder.fillQuantityExponent(buffer, offset, index) != exponent) {
        throw new StarbaseProtocolException("immediate-fill quantity exponent mismatch");
      }
      long fillQuantity = NewOrderResponseDecoder.fillQuantityMantissa(buffer, offset, index);
      try {
        fillTotal = Math.addExact(fillTotal, fillQuantity);
      } catch (ArithmeticException overflow) {
        throw new StarbaseProtocolException("immediate-fill quantity overflow");
      }
      if (!fillProcessor.onImmediateFill(
          session.sessionId,
          NewOrderResponseDecoder.fillMatchId(buffer, offset, index),
          orderId,
          fillQuantity)) {
        throw new StarbaseProtocolException("duplicate or unmatched immediate fill");
      }
    }
    if (fillTotal != totalFilled) {
      throw new StarbaseProtocolException("immediate fills do not equal totalFilled");
    }
    long remaining = orders.remainingQuantity(orderId);
    if (!orders.applyStatus(session.sessionId, eventSequence, orderId, remaining, status)) {
      throw new StarbaseProtocolException("new-order response status could not be applied");
    }
    if (!session.correlations.complete(correlationId, status, orderId)) {
      throw new StarbaseProtocolException("new-order correlation completed twice");
    }
    orderEvents.publish(
        clientOrderId, orderId, NewOrderResponseDecoder.timestampNanos(buffer, offset));
  }

  private void onNewOrderReject(Session session, ByteBuffer buffer, int offset) {
    long correlationId = NewOrderRejectDecoder.correlationId(buffer, offset);
    long clientOrderId = NewOrderRejectDecoder.clientOrderId(buffer, offset);
    requireCorrelation(session, correlationId, OrderCommandFacade.COMMAND_NEW, clientOrderId);
    if (orders.instrumentIdByClientOrderId(clientOrderId)
        != NewOrderRejectDecoder.instrumentId(buffer, offset)) {
      throw new StarbaseProtocolException("new-order reject instrument mismatch");
    }
    long eventSequence = TcpHeaderCodec.sequenceNumber(buffer, offset);
    if (!orders.reject(session.sessionId, eventSequence, clientOrderId)) {
      throw new StarbaseProtocolException("new-order reject does not match pending state");
    }
    if (!session.correlations.complete(
        correlationId,
        NewOrderRejectDecoder.reason(buffer, offset),
        NewOrderRejectDecoder.orderId(buffer, offset))) {
      throw new StarbaseProtocolException("new-order reject correlation completed twice");
    }
    orderEvents.publish(
        clientOrderId,
        NewOrderRejectDecoder.orderId(buffer, offset),
        NewOrderRejectDecoder.timestampNanos(buffer, offset));
  }

  private void onOrderFilled(Session session, ByteBuffer buffer, int offset) {
    int fillCount = OrderFilledDecoder.fillCount(buffer, offset);
    for (int index = 0; index < fillCount; index++) {
      long orderId = OrderFilledDecoder.orderId(buffer, offset, index);
      long clientOrderId = OrderFilledDecoder.clientOrderId(buffer, offset, index);
      long instrumentId = OrderFilledDecoder.instrumentId(buffer, offset, index);
      if (orders.clientOrderId(orderId) != clientOrderId
          || orders.instrumentId(orderId) != instrumentId) {
        throw new StarbaseProtocolException("unsolicited fill identity mismatch");
      }
      if (OrderFilledDecoder.fillQuantityExponent(buffer, offset, index)
              != OrderFilledDecoder.totalFilledExponent(buffer, offset, index)
          || OrderFilledDecoder.fillQuantityExponent(buffer, offset, index)
              != orders.quantityExponent(orderId)) {
        throw new StarbaseProtocolException("unsolicited fill quantity exponent mismatch");
      }
      long matchId = OrderFilledDecoder.matchId(buffer, offset, index);
      if (fillProcessor.containsMatchId(matchId)) {
        continue;
      }
      long fillQuantity = OrderFilledDecoder.fillQuantityMantissa(buffer, offset, index);
      long totalFilled = OrderFilledDecoder.totalFilledMantissa(buffer, offset, index);
      long expectedRemaining;
      try {
        expectedRemaining = Math.subtractExact(orders.originalQuantity(orderId), totalFilled);
      } catch (ArithmeticException overflow) {
        throw new StarbaseProtocolException("unsolicited fill total overflow");
      }
      if (expectedRemaining < 0
          || orders.remainingQuantity(orderId) - fillQuantity != expectedRemaining) {
        throw new StarbaseProtocolException("unsolicited fill total does not match local state");
      }
      if (!fillProcessor.onUnsolicitedFill(
          session.sessionId, matchId, orderId, fillQuantity)) {
        throw new StarbaseProtocolException("unsolicited fill could not be applied");
      }
    }
  }

  private void onAmendOrderResponse(Session session, ByteBuffer buffer, int offset) {
    long correlationId = AmendOrderResponseDecoder.correlationId(buffer, offset);
    long clientOrderId = AmendOrderResponseDecoder.clientOrderId(buffer, offset);
    requireCorrelation(session, correlationId, OrderCommandFacade.COMMAND_AMEND, clientOrderId);
    long orderId = AmendOrderResponseDecoder.orderId(buffer, offset);
    long instrumentId = AmendOrderResponseDecoder.instrumentId(buffer, offset);
    if (orders.clientOrderId(orderId) != clientOrderId
        || orders.instrumentId(orderId) != instrumentId) {
      throw new StarbaseProtocolException("amend response identity mismatch");
    }
    if (AmendOrderResponseDecoder.quantityExponent(buffer, offset)
            != AmendOrderResponseDecoder.totalFilledExponent(buffer, offset)
        || AmendOrderResponseDecoder.quantityExponent(buffer, offset)
            != orders.quantityExponent(orderId)) {
      throw new StarbaseProtocolException("amend response quantity exponent mismatch");
    }
    long remaining;
    try {
      remaining =
          Math.subtractExact(
              AmendOrderResponseDecoder.quantityMantissa(buffer, offset),
              AmendOrderResponseDecoder.totalFilledMantissa(buffer, offset));
    } catch (ArithmeticException overflow) {
      throw new StarbaseProtocolException("amend response quantity overflow");
    }
    int status = AmendOrderResponseDecoder.status(buffer, offset);
    long priorFilled = orders.originalQuantity(orderId) - orders.remainingQuantity(orderId);
    long expectedNewFillTotal;
    try {
      expectedNewFillTotal =
          Math.subtractExact(
              AmendOrderResponseDecoder.totalFilledMantissa(buffer, offset), priorFilled);
    } catch (ArithmeticException overflow) {
      throw new StarbaseProtocolException("amend response fill total overflow");
    }
    long groupFillTotal = 0;
    int fillCount = AmendOrderResponseDecoder.fillCount(buffer, offset);
    for (int index = 0; index < fillCount; index++) {
      if (AmendOrderResponseDecoder.fillQuantityExponent(buffer, offset, index)
          != AmendOrderResponseDecoder.quantityExponent(buffer, offset)) {
        throw new StarbaseProtocolException("amend immediate-fill exponent mismatch");
      }
      try {
        groupFillTotal =
            Math.addExact(
                groupFillTotal,
                AmendOrderResponseDecoder.fillQuantityMantissa(buffer, offset, index));
      } catch (ArithmeticException overflow) {
        throw new StarbaseProtocolException("amend immediate-fill overflow");
      }
    }
    if (expectedNewFillTotal < 0 || groupFillTotal != expectedNewFillTotal) {
      throw new StarbaseProtocolException("amend immediate fills do not equal totalFilled delta");
    }
    long eventSequence = TcpHeaderCodec.sequenceNumber(buffer, offset);
    if (!orders.amendOutcome(
        session.sessionId,
        eventSequence,
        orderId,
        AmendOrderResponseDecoder.quantityMantissa(buffer, offset),
        remaining,
        status)) {
      throw new StarbaseProtocolException("amend response could not be applied");
    }
    for (int index = 0; index < fillCount; index++) {
      long matchId = AmendOrderResponseDecoder.fillMatchId(buffer, offset, index);
      if (!fillProcessor.containsMatchId(matchId)
          && !fillProcessor.onAuthoritativeFill(
              session.sessionId,
              matchId,
              orderId,
              AmendOrderResponseDecoder.fillQuantityMantissa(buffer, offset, index),
              remaining)) {
        throw new StarbaseProtocolException("amend immediate fill could not be recorded");
      }
    }
    if (!session.correlations.complete(correlationId, status, orderId)) {
      throw new StarbaseProtocolException("amend correlation completed twice");
    }
    orderEvents.publish(
        clientOrderId, orderId, AmendOrderResponseDecoder.timestampNanos(buffer, offset));
  }

  private void onCancelOrderResponse(Session session, ByteBuffer buffer, int offset) {
    long correlationId = CancelOrderResponseDecoder.correlationId(buffer, offset);
    long clientOrderId = CancelOrderResponseDecoder.clientOrderId(buffer, offset);
    requireCorrelation(session, correlationId, OrderCommandFacade.COMMAND_CANCEL, clientOrderId);
    long orderId = CancelOrderResponseDecoder.orderId(buffer, offset);
    long instrumentId = CancelOrderResponseDecoder.instrumentId(buffer, offset);
    if (orders.clientOrderId(orderId) != clientOrderId
        || orders.instrumentId(orderId) != instrumentId) {
      throw new StarbaseProtocolException("cancel response identity mismatch");
    }
    if (!orders.cancel(
        session.sessionId, TcpHeaderCodec.sequenceNumber(buffer, offset), orderId)) {
      throw new StarbaseProtocolException("cancel response could not be applied");
    }
    if (!session.correlations.complete(correlationId, 0, orderId)) {
      throw new StarbaseProtocolException("cancel correlation completed twice");
    }
    orderEvents.publish(
        clientOrderId, orderId, CancelOrderResponseDecoder.timestampNanos(buffer, offset));
  }

  private void onAmendOrderReject(Session session, ByteBuffer buffer, int offset) {
    long correlationId = AmendOrderRejectDecoder.correlationId(buffer, offset);
    long clientOrderId = AmendOrderRejectDecoder.clientOrderId(buffer, offset);
    requireCorrelation(session, correlationId, OrderCommandFacade.COMMAND_AMEND, clientOrderId);
    long orderId = AmendOrderRejectDecoder.orderId(buffer, offset);
    if (orders.clientOrderId(orderId) != clientOrderId
        || orders.instrumentId(orderId) != AmendOrderRejectDecoder.instrumentId(buffer, offset)
        || !session.correlations.complete(
            correlationId, AmendOrderRejectDecoder.reason(buffer, offset), orderId)) {
      throw new StarbaseProtocolException("amend reject identity mismatch");
    }
    orderEvents.publish(
        clientOrderId, orderId, AmendOrderRejectDecoder.timestampNanos(buffer, offset));
  }

  private void onCancelOrderReject(Session session, ByteBuffer buffer, int offset) {
    long correlationId = CancelOrderRejectDecoder.correlationId(buffer, offset);
    long clientOrderId = CancelOrderRejectDecoder.clientOrderId(buffer, offset);
    requireCorrelation(session, correlationId, OrderCommandFacade.COMMAND_CANCEL, clientOrderId);
    long orderId = CancelOrderRejectDecoder.orderId(buffer, offset);
    if (!CancelOrderRejectDecoder.isOrderIdNull(buffer, offset)
        && (orders.clientOrderId(orderId) != clientOrderId
            || orders.instrumentId(orderId)
                != CancelOrderRejectDecoder.instrumentId(buffer, offset))) {
      throw new StarbaseProtocolException("cancel reject identity mismatch");
    }
    if (!session.correlations.complete(
        correlationId, CancelOrderRejectDecoder.reason(buffer, offset), orderId)) {
      throw new StarbaseProtocolException("cancel reject correlation mismatch");
    }
    orderEvents.publish(
        clientOrderId, orderId, CancelOrderRejectDecoder.timestampNanos(buffer, offset));
  }

  private void onMassCancelResponse(Session session, ByteBuffer buffer, int offset) {
    long correlationId = MassCancelResponseDecoder.correlationId(buffer, offset);
    if (session.correlations.state(correlationId) != CorrelationTable.STATE_PENDING
        || session.correlations.commandType(correlationId)
            != OrderCommandFacade.COMMAND_MASS_CANCEL
        || !session.correlations.complete(
            correlationId, MassCancelResponseDecoder.totalOrderCount(buffer, offset), Long.MIN_VALUE)) {
      throw new StarbaseProtocolException("mass-cancel correlation mismatch");
    }
  }

  private void onMassCancelReject(Session session, ByteBuffer buffer, int offset) {
    long correlationId = MassCancelRejectDecoder.correlationId(buffer, offset);
    if (session.correlations.state(correlationId) != CorrelationTable.STATE_PENDING
        || session.correlations.commandType(correlationId)
            != OrderCommandFacade.COMMAND_MASS_CANCEL
        || !session.correlations.complete(
            correlationId,
            MassCancelRejectDecoder.reason(buffer, offset),
            Long.MIN_VALUE)) {
      throw new StarbaseProtocolException("mass-cancel reject correlation mismatch");
    }
  }

  private void onOrderPlaced(Session session, ByteBuffer buffer, int offset) {
    long clientOrderId = OrderPlacedDecoder.clientOrderId(buffer, offset);
    long orderId = OrderPlacedDecoder.orderId(buffer, offset);
    long instrumentId = OrderPlacedDecoder.instrumentId(buffer, offset);
    if (orders.stateByOrderId(orderId) != LocalOrderStateStore.STATE_QUEUED
        || orders.clientOrderId(orderId) != clientOrderId
        || orders.instrumentId(orderId) != instrumentId
        || router.originSessionId(clientOrderId) != session.sessionId) {
      throw new StarbaseProtocolException("OrderPlaced identity does not match queued state");
    }
    int exponent = OrderPlacedDecoder.quantityExponent(buffer, offset);
    if (OrderPlacedDecoder.totalFilledExponent(buffer, offset) != exponent
        || orders.quantityExponent(orderId) != exponent) {
      throw new StarbaseProtocolException("OrderPlaced quantity exponent mismatch");
    }
    long quantity = OrderPlacedDecoder.quantityMantissa(buffer, offset);
    long totalFilled = OrderPlacedDecoder.totalFilledMantissa(buffer, offset);
    long priorFilled = orders.originalQuantity(orderId) - orders.remainingQuantity(orderId);
    long newFillTotal;
    try {
      newFillTotal = Math.subtractExact(totalFilled, priorFilled);
    } catch (ArithmeticException overflow) {
      throw new StarbaseProtocolException("OrderPlaced fill total overflow");
    }
    long groupTotal = 0;
    int fillCount = OrderPlacedDecoder.fillCount(buffer, offset);
    for (int index = 0; index < fillCount; index++) {
      if (OrderPlacedDecoder.fillQuantityExponent(buffer, offset, index) != exponent) {
        throw new StarbaseProtocolException("OrderPlaced fill exponent mismatch");
      }
      long fillQuantity = OrderPlacedDecoder.fillQuantityMantissa(buffer, offset, index);
      try {
        groupTotal = Math.addExact(groupTotal, fillQuantity);
      } catch (ArithmeticException overflow) {
        throw new StarbaseProtocolException("OrderPlaced group fill overflow");
      }
      if (!fillProcessor.onImmediateFill(
          session.sessionId,
          OrderPlacedDecoder.fillMatchId(buffer, offset, index),
          orderId,
          fillQuantity)) {
        throw new StarbaseProtocolException("OrderPlaced fill could not be applied");
      }
    }
    if (groupTotal != newFillTotal || quantity - totalFilled != orders.remainingQuantity(orderId)) {
      throw new StarbaseProtocolException("OrderPlaced total does not match queued state");
    }
    long eventSequence = TcpHeaderCodec.sequenceNumber(buffer, offset);
    if (!orders.applyStatus(
        session.sessionId,
        eventSequence,
        orderId,
        orders.remainingQuantity(orderId),
        OrderPlacedDecoder.status(buffer, offset))) {
      throw new StarbaseProtocolException("OrderPlaced status could not be applied");
    }
    orderEvents.publish(
        clientOrderId, orderId, OrderPlacedDecoder.timestampNanos(buffer, offset));
  }

  private void onOrdersCanceled(Session session, ByteBuffer buffer, int offset) {
    long eventSequence = TcpHeaderCodec.sequenceNumber(buffer, offset);
    int count = OrdersCanceledDecoder.orderCount(buffer, offset);
    for (int index = 0; index < count; index++) {
      if (OrdersCanceledDecoder.flags(buffer, offset, index) != 0) {
        throw new StarbaseProtocolException("mass-quote cancellation is not assembled");
      }
      long clientOrderId = OrdersCanceledDecoder.clientOrderId(buffer, offset, index);
      long orderId = OrdersCanceledDecoder.orderId(buffer, offset, index);
      long instrumentId = OrdersCanceledDecoder.instrumentId(buffer, offset, index);
      if (orders.clientOrderId(orderId) != clientOrderId
          || orders.instrumentId(orderId) != instrumentId) {
        throw new StarbaseProtocolException("OrdersCanceled identity mismatch");
      }
      long observedFilled = orders.originalQuantity(orderId) - orders.remainingQuantity(orderId);
      if (OrdersCanceledDecoder.totalFilledMantissa(buffer, offset, index) != observedFilled
          || OrdersCanceledDecoder.totalFilledExponent(buffer, offset, index)
              != orders.quantityExponent(orderId)) {
        throw new StarbaseProtocolException("OrdersCanceled fill total mismatch");
      }
      if (!orders.cancel(session.sessionId, eventSequence, orderId)) {
        throw new StarbaseProtocolException("OrdersCanceled state could not be applied");
      }
      orderEvents.publish(
          clientOrderId, orderId, OrdersCanceledDecoder.timestampNanos(buffer, offset));
    }
  }

  private void requireCorrelation(
      Session session, long correlationId, int commandType, long clientOrderId) {
    if (session.correlations.state(correlationId) != CorrelationTable.STATE_PENDING
        || session.correlations.commandType(correlationId) != commandType
        || session.correlations.clientOrderId(correlationId) != clientOrderId) {
      throw new StarbaseProtocolException("response correlation identity mismatch");
    }
  }

  private void requireNewOrderRoute() {
    if (!router.hasReadyEndpoint(context.productGroup())) {
      throw new IllegalStateException("no ready order-entry endpoint for " + context.productGroup());
    }
  }

  private Session originSession(long clientOrderId, long instrumentId) {
    if (orders.instrumentIdByClientOrderId(clientOrderId) != instrumentId) {
      throw new IllegalArgumentException("client order instrument mismatch");
    }
    long sessionId = router.originSessionId(clientOrderId);
    return sessionId == SIDE_A_SESSION_ID ? sideA : sideB;
  }

  private CorrelationTable correlationTable(long correlationId) {
    if (sideA.correlations.state(correlationId) != CorrelationTable.STATE_EMPTY) {
      return sideA.correlations;
    }
    if (sideB.correlations.state(correlationId) != CorrelationTable.STATE_EMPTY) {
      return sideB.correlations;
    }
    throw new IllegalArgumentException("unknown correlationId: " + correlationId);
  }

  private void registerPending(
      long clientOrderId,
      long instrumentId,
      int side,
      long price,
      long quantity,
      int quantityExponent) {
    int stateSide = side == 1 ? 1 : 2;
    if (!orders.registerPending(
        clientOrderId, instrumentId, stateSide, price, quantity, quantityExponent)) {
      throw new IllegalStateException("client order is already live");
    }
  }

  private void stageNew(
      int kind,
      long clientOrderId,
      long instrumentId,
      long priceMantissa,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      long selfMatchPreventionId,
      int side,
      int timeInForce,
      int flags,
      int selfTradingMode) {
    stagedKind = kind;
    stagedClientOrderId = clientOrderId;
    stagedInstrumentId = instrumentId;
    stagedPriceMantissa = priceMantissa;
    stagedQuantityMantissa = quantityMantissa;
    stagedQuantityExponent = quantityExponent;
    stagedShowQuantityNull = showQuantityNull;
    stagedShowQuantityMantissa = showQuantityMantissa;
    stagedSelfMatchPreventionId = selfMatchPreventionId;
    stagedSide = side;
    stagedTimeInForce = timeInForce;
    stagedFlags = flags;
    stagedSelfTradingMode = selfTradingMode;
  }

  private Session session(GatewaySide side) {
    return side == GatewaySide.A ? sideA : sideB;
  }

  private void requireAssembled() {
    if (!assembled) {
      throw new IllegalStateException("redundant order-entry lifecycle is not configured");
    }
  }

  private boolean globallySequenceSafe() {
    return sideA.sequenceSafe && sideB.sequenceSafe;
  }

  private void onSessionDisconnected(Session disconnected) {
    recoveryEpoch = recoveryEpoch == Long.MAX_VALUE ? Long.MAX_VALUE : recoveryEpoch + 1;
    globalRecoverySafe = false;
    Session peer = disconnected == sideA ? sideB : sideA;
    peer.requireFreshRecovery();
  }

  private void onSessionReconciled(long reconciledEpoch) {
    synchronized (commandLock) {
      if (reconciledEpoch != recoveryEpoch) {
        return;
      }
      if (reconciledEpoch != 0 && fillResetEpoch != reconciledEpoch) {
        fillProcessor.resetAfterReconciliation();
        fillResetEpoch = reconciledEpoch;
      }
      globalRecoverySafe = true;
    }
  }

  private static StarbaseCredentials copyCredentials(StarbaseCredentials source) {
    return new StarbaseCredentials(source.copyUsername(), source.copyPassword());
  }

  private static StarbaseOrderEntryContext[] validatePair(
      StarbaseOrderEntryContext first, StarbaseOrderEntryContext second) {
    Objects.requireNonNull(first, "firstContext");
    Objects.requireNonNull(second, "secondContext");
    if (first.productGroup() != second.productGroup()) {
      throw new IllegalArgumentException("order-entry A/B contexts must use one product group");
    }
    if (first.gatewaySide() == second.gatewaySide()) {
      throw new IllegalArgumentException("order-entry A/B contexts must use opposite sides");
    }
    return first.gatewaySide() == GatewaySide.A
        ? new StarbaseOrderEntryContext[] {first, second}
        : new StarbaseOrderEntryContext[] {second, first};
  }

  private final class Session implements OrderRouteEndpoint, OrderEntryMessageHandler {

    private final long sessionId;
    private final StarbaseOrderEntryContext sessionContext;
    private final StarbaseCredentials sessionCredentials;
    private final CorrelationTable correlations;
    private final SessionSequenceState sequences = new SessionSequenceState(1, 1);
    private final ReconnectReadiness readiness;
    private final OrderStateReconciliation reconciliation;
    private final ResendEncoder resendEncoder = new ResendEncoder();

    private OrderEntryConnection connection;
    private AuthenticationStateMachine authentication;
    private SessionLiveness liveness;
    private OrderCommandFacade commands;
    private boolean logonPending;
    private boolean everAuthenticated;
    private boolean disconnectHandled;
    private boolean closed;
    private volatile boolean sequenceSafe = true;
    private long recoveryAttemptAtNanos;

    private Session(
        long sessionId,
        StarbaseOrderEntryContext sessionContext,
        StarbaseCredentials sessionCredentials,
        long initialCorrelationId,
        int correlationCapacity) {
      this.sessionId = sessionId;
      this.sessionContext = sessionContext;
      this.sessionCredentials = sessionCredentials;
      correlations = new CorrelationTable(correlationCapacity, initialCorrelationId);
      readiness =
          new ReconnectReadiness(
              sessionContext.clock(),
              INITIAL_RECONNECT_BACKOFF,
              MAXIMUM_RECONNECT_BACKOFF,
              state ->
                  sessionEvents.publish(sessionId, state, sessionContext.clock().nanoTime()));
      reconciliation =
          new OrderStateReconciliation(
              sessionContext.clock(),
              MAXIMUM_SNAPSHOT_AGE,
              orders,
              recoveryCache,
              readiness);
    }

    private void start() {
      readiness.start();
    }

    private void poll() {
      if (closed) {
        return;
      }
      try {
        if (connection != null && connection.isFailed()) {
          disconnect();
        }
        if (readiness.poll() == ReconnectReadiness.ACTION_CONNECT) {
          connect();
        }
        if (connection == null || !connection.isRunning()) {
          return;
        }
        if (authentication != null && !authentication.isAuthenticated()) {
          if (logonPending && connection.writer().pendingBytes() != 0) {
            logonPending = !authentication.flushLogon();
          }
          if (authentication.checkTimeout() || authentication.isFailed()) {
            disconnect();
          }
          return;
        }
        if (connection.writer().pendingBytes() != 0) {
          connection.flush();
        }
        if (liveness != null
            && liveness.poll(sequences) == SessionLiveness.ACTION_DISCONNECT) {
          disconnect();
          return;
        }
        long expired = correlations.expireNext(sessionContext.clock().nanoTime());
        if (expired != 0) {
          disconnect();
          return;
        }
        if (readiness.state() == ReconnectReadiness.STATE_RECONCILING
            && deadlineReached(sessionContext.clock().nanoTime(), recoveryAttemptAtNanos)) {
          long attemptEpoch = recoveryEpoch;
          if (reconciliation.reconcile()) {
            onSessionReconciled(attemptEpoch);
          } else {
            recoveryAttemptAtNanos = recoveryCache.nextRefreshNanos();
          }
        }
      } catch (RuntimeException failure) {
        disconnect();
      }
    }

    private void connect() {
      if (!everAuthenticated) {
        sequences.reset(1, 1);
      }
      try {
        OrderEntryDuplexTransport transport =
            Objects.requireNonNull(transportFactory.apply(sessionContext), "transport");
        connection =
            new OrderEntryConnection(
                transport,
                sessionContext.receiveBufferBytes(),
                sessionContext.sendBufferBytes(),
                this);
        connection.start();
        readiness.onConnected();
        authentication =
            new AuthenticationStateMachine(
                connection.writer(),
                sessionContext.clock(),
                sessionContext.connectTimeout(),
                sessionCredentials);
        long sequence = sequences.claimOutboundSequence();
        logonPending =
            !authentication.begin(sequence, sequences.lastProcessedInbound(), !everAuthenticated);
        disconnectHandled = false;
      } catch (RuntimeException failure) {
        if (connection != null) {
          connection.close();
          connection = null;
        }
        if (readiness.state() == ReconnectReadiness.STATE_CONNECTING) {
          readiness.onConnectFailed();
        } else if (readiness.isConnected()) {
          readiness.onDisconnected();
        }
      }
    }

    @Override
    public synchronized void onMessage(int templateId, ByteBuffer buffer, int offset) {
      long sequence = TcpHeaderCodec.sequenceNumber(buffer, offset);
      long acknowledgment = TcpHeaderCodec.lastProcessedSequenceNumber(buffer, offset);
      boolean resend =
          (TcpHeaderCodec.flags(buffer, offset) & TcpHeaderCodec.FLAG_RESEND) != 0;
      int action;
      if (templateId == GapFillDecoder.TEMPLATE_ID) {
        action =
            sequences.onGapFill(
                sequence,
                acknowledgment,
                resend,
                GapFillDecoder.newSequenceNumber(buffer, offset));
      } else {
        action = sequences.onInbound(sequence, acknowledgment, resend);
      }
      if (liveness != null) {
        liveness.onPeerActivity();
      }
      if (action == SessionSequenceState.ACTION_DUPLICATE) {
        return;
      }
      if (authentication == null || !authentication.isAuthenticated()) {
        if (templateId != LogonConfirmationCodec.TEMPLATE_ID
            && templateId != SessionRejectDecoder.TEMPLATE_ID) {
          throw new StarbaseProtocolException("unexpected pre-authentication message");
        }
        authentication.onMessage(templateId, buffer, offset);
        if (authentication.isAuthenticated()) {
          onAuthenticated();
        }
        if (action == SessionSequenceState.ACTION_RESEND) {
          sequenceSafe = false;
          readiness.setSequenceValid(false);
          sendResend();
        }
        return;
      }
      if (action == SessionSequenceState.ACTION_RESEND) {
        sequenceSafe = false;
        readiness.setSequenceValid(false);
        sendResend();
        return;
      }
      sequenceSafe = sequences.resendFromSequence() == 0;
      readiness.setSequenceValid(sequenceSafe);
      switch (templateId) {
        case HeartbeatCodec.TEMPLATE_ID, GapFillDecoder.TEMPLATE_ID -> {
          return;
        }
        case TestRequestCodec.TEMPLATE_ID -> {
          liveness.onTestRequest(buffer, offset, sequences);
          return;
        }
        case ResendRequestCodec.TEMPLATE_ID ->
            throw new StarbaseProtocolException("gateway sent a client-only ResendRequest");
        case LoggedOutCodec.TEMPLATE_ID, SessionRejectDecoder.TEMPLATE_ID ->
            throw new StarbaseProtocolException("gateway terminated or rejected the session");
        case LogonConfirmationCodec.TEMPLATE_ID ->
            throw new StarbaseProtocolException("duplicate LogonConfirmation");
        default -> onApplicationMessage(this, templateId, buffer, offset);
      }
    }

    private void onAuthenticated() {
      everAuthenticated = true;
      liveness =
          new SessionLiveness(
              connection.writer(),
              sessionContext.clock(),
              Duration.ofSeconds(authentication.heartbeatIntervalSeconds()),
              sessionContext.inactivityTimeout());
      liveness.start();
      commands =
          new OrderCommandFacade(
              readiness,
              connection.writer(),
              correlations,
              sequences,
              sessionContext.clock(),
              sessionContext.inactivityTimeout().toNanos());
      readiness.onAuthenticated();
      sequenceSafe = sequences.resendFromSequence() == 0;
      readiness.setSequenceValid(sequenceSafe);
      readiness.setReferenceReady(referenceDataReady);
      recoveryAttemptAtNanos = sessionContext.clock().nanoTime();
    }

    private void sendResend() {
      if (connection.writer().pendingBytes() != 0) {
        throw new StarbaseProtocolException("cannot request resend while a frame is pending");
      }
      resendEncoder.fromSequence = sequences.resendFromSequence();
      resendEncoder.sequence = sequences.claimOutboundSequence();
      resendEncoder.lastProcessedSequence = sequences.lastProcessedInbound();
      connection.write(resendEncoder);
    }

    private void setReferenceDataReady(boolean ready) {
      int state = readiness.state();
      if (state == ReconnectReadiness.STATE_RECONCILING
          || state == ReconnectReadiness.STATE_READY) {
        readiness.setReferenceReady(ready);
        if (ready) {
          recoveryAttemptAtNanos = sessionContext.clock().nanoTime();
        }
      }
    }

    private void disconnect() {
      if (disconnectHandled || closed) {
        return;
      }
      disconnectHandled = true;
      if (connection != null) {
        connection.close();
        connection = null;
      }
      if (authentication != null) {
        authentication.close();
        authentication = null;
      }
      liveness = null;
      commands = null;
      if (readiness.isConnected()) {
        reconciliation.onDisconnected();
        if (everAuthenticated) {
          onSessionDisconnected(this);
        }
      }
    }

    private void requireFreshRecovery() {
      if (closed) {
        return;
      }
      reconciliation.requireFreshSnapshot();
      recoveryAttemptAtNanos = sessionContext.clock().nanoTime();
    }

    private void requireCommandReady() {
      if (!isReady() || commands == null) {
        throw new IllegalStateException("origin order-entry session is not ready");
      }
    }

    private void close() {
      closed = true;
      if (connection != null) {
        connection.close();
        connection = null;
      }
      if (authentication != null) {
        authentication.close();
        authentication = null;
      }
      readiness.close();
    }

    private boolean isAuthenticated() {
      return authentication != null && authentication.isAuthenticated();
    }

    @Override
    public long sessionId() {
      return sessionId;
    }

    @Override
    public ProductGroup productGroup() {
      return sessionContext.productGroup();
    }

    @Override
    public GatewaySide gatewaySide() {
      return sessionContext.gatewaySide();
    }

    @Override
    public boolean isReady() {
      return readiness.isReady() && globalRecoverySafe && globallySequenceSafe();
    }

    @Override
    public long submitNewOrder(long clientOrderId) {
      synchronized (commandLock) {
        if (commands == null || stagedClientOrderId != clientOrderId) {
          throw new IllegalStateException("no staged new order for endpoint");
        }
        return switch (stagedKind) {
          case 1 ->
              commands.newLimit(
                  stagedClientOrderId,
                  stagedInstrumentId,
                  stagedPriceMantissa,
                  stagedQuantityMantissa,
                  stagedQuantityExponent,
                  stagedShowQuantityNull,
                  stagedShowQuantityMantissa,
                  stagedSelfMatchPreventionId,
                  stagedSide,
                  stagedTimeInForce,
                  stagedFlags,
                  stagedSelfTradingMode);
          case 2 ->
              commands.newMarket(
                  stagedClientOrderId,
                  stagedInstrumentId,
                  stagedQuantityMantissa,
                  stagedQuantityExponent,
                  stagedShowQuantityNull,
                  stagedShowQuantityMantissa,
                  stagedSelfMatchPreventionId,
                  stagedSide,
                  stagedTimeInForce,
                  stagedFlags,
                  stagedSelfTradingMode);
          default -> throw new IllegalStateException("no staged new-order command");
        };
      }
    }

    private final class ResendEncoder implements TcpFrameEncoder {
      private long fromSequence;
      private long sequence;
      private long lastProcessedSequence;

      @Override
      public int encode(ByteBuffer buffer, int offset) {
        return ResendRequestCodec.encode(
            buffer,
            offset,
            fromSequence,
            0,
            sequence,
            lastProcessedSequence,
            sessionContext.clock().nanoTime());
      }
    }
  }

  private static boolean deadlineReached(long now, long deadline) {
    return now - deadline >= 0;
  }
}
