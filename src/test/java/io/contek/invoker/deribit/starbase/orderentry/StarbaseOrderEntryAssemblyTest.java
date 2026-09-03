package io.contek.invoker.deribit.starbase.orderentry;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.HeartbeatCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.AmendOrderRequestDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.CancelOrderByIdRequestDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.CancelOrderRequestDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.LogonConfirmationCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.LogonDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.MassCancelRequestDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.NewOrderRequestDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.ResendRequestCodec;
import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.NanoClock;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.common.StarbaseCredentials;
import io.contek.invoker.deribit.starbase.orderentry.connection.OrderEntryDuplexTransport;
import io.contek.invoker.deribit.starbase.orderentry.connection.ReconnectReadiness;
import io.contek.invoker.deribit.starbase.orderentry.state.ClientOrderIdMap;
import io.contek.invoker.deribit.starbase.orderentry.state.LocalOrderStateStore;
import io.contek.invoker.deribit.starbase.orderentry.state.OrderStateReconciliation;
import io.contek.invoker.deribit.starbase.rest.OpenOrderRecoveryCache;
import io.contek.invoker.deribit.starbase.rest.StarbaseOpenOrder;
import io.contek.invoker.deribit.starbase.rest.StarbaseOrderSide;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestOrderState;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestOrderType;
import io.contek.invoker.deribit.starbase.rest.StarbaseTimeInForce;
import com.sun.management.ThreadMXBean;
import java.math.BigDecimal;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicInteger;

public final class StarbaseOrderEntryAssemblyTest {

  public void testPairedLifecycleAuthenticatesReconcilesAndRoutesOneNewOrderExactlyOnce()
      throws Exception {
    MutableClock clock = new MutableClock();
    ScriptedTransport sideA = new ScriptedTransport();
    ScriptedTransport sideB = new ScriptedTransport();
    OpenOrderRecoveryCache recovery =
        new OpenOrderRecoveryCache(
            clock, OpenOrderRecoveryCache.MINIMUM_REFRESH_INTERVAL, List::of);
    long[] orderEvent = new long[3];
    try (StarbaseCredentials credentialsA = credentials('a');
        StarbaseCredentials credentialsB = credentials('b');
        StarbaseOrderEntryApi api =
            pairedApi(clock, credentialsA, credentialsB, recovery, sideA, sideB)) {
      assertFalse(api.isConnected(GatewaySide.A));
      assertEquals(ReconnectReadiness.STATE_NEW, api.sessionState(GatewaySide.A));
      assertEquals(
          OrderStateReconciliation.RESULT_NONE,
          api.reconciliationResult(GatewaySide.A));
      assertFalse(api.isReferenceDataReady());
      api.getOrderEventsChannel()
          .addListener(
              (key, value, timestamp) -> {
                orderEvent[0] = key;
                orderEvent[1] = value;
                orderEvent[2] = timestamp;
              });
      api.setReferenceDataReady(true);
      assertTrue(api.isReferenceDataReady());
      api.start();

      await(() -> sideA.outboundCount() == 1 && sideB.outboundCount() == 1);
      assertLogon(sideA.outboundFrame(0));
      assertLogon(sideB.outboundFrame(0));
      sideA.enqueue(logonConfirmation(1, 1));
      sideB.enqueue(logonConfirmation(1, 1));

      await(() -> api.isReady(GatewaySide.A) && api.isReady(GatewaySide.B));
      assertTrue(api.isConnected(GatewaySide.A));
      assertTrue(api.isAuthenticated(GatewaySide.A));
      assertTrue(api.isAuthenticated(GatewaySide.B));
      assertEquals(ReconnectReadiness.STATE_READY, api.sessionState(GatewaySide.A));
      assertEquals(
          OrderStateReconciliation.RESULT_MATCHED,
          api.reconciliationResult(GatewaySide.A));
      api.setReferenceDataReady(false);
      assertFalse(api.isReady());
      api.setReferenceDataReady(true);
      await(() -> api.isReady(GatewaySide.A) && api.isReady(GatewaySide.B));
      long correlationId =
          api.newLimit(101, 501, 25_000_000_000L, 10, -2, true, 0, 0, 1, 0, 0, 0);

      await(() -> sideA.outboundCount() == 2);
      assertEquals(1, sideB.outboundCount());
      ByteBuffer request = sideA.outboundFrame(1);
      NewOrderRequestDecoder.validate(request, 0);
      assertEquals(101L, NewOrderRequestDecoder.clientOrderId(request, 0));
      assertEquals(correlationId, NewOrderRequestDecoder.correlationId(request, 0));
      assertEquals(LocalOrderStateStore.STATE_PENDING, api.orderStateByClientOrderId(101));

      sideA.enqueue(newOrderResponse(2, 2, 101, correlationId, 9001, 501, 10, 0));
      await(
          () ->
              api.orderStateByOrderId(9001) == LocalOrderStateStore.STATE_OPEN
                  && orderEvent[0] == 101);
      assertEquals(101L, orderEvent[0]);
      assertEquals(9001L, orderEvent[1]);
      assertEquals(12_345L, orderEvent[2]);
      assertEquals(10L, api.remainingQuantity(9001));
    }
    assertTrue(sideA.isClosed());
    assertTrue(sideB.isClosed());
  }

  public void testStringClientIdsAndSignedExchangeOrderIdCancelStayExact() throws Exception {
    MutableClock clock = new MutableClock();
    ScriptedTransport sideA = new ScriptedTransport();
    ScriptedTransport sideB = new ScriptedTransport();
    OpenOrderRecoveryCache recovery =
        new OpenOrderRecoveryCache(
            clock, OpenOrderRecoveryCache.MINIMUM_REFRESH_INTERVAL, List::of);
    try (StarbaseCredentials credentialsA = credentials('a');
        StarbaseCredentials credentialsB = credentials('b');
        StarbaseOrderEntryApi api =
            pairedApi(clock, credentialsA, credentialsB, recovery, sideA, sideB)) {
      api.setReferenceDataReady(true);
      api.start();
      await(() -> sideA.outboundCount() == 1 && sideB.outboundCount() == 1);
      sideA.enqueue(logonConfirmation(1, 1));
      sideB.enqueue(logonConfirmation(1, 1));
      await(() -> api.isReady(GatewaySide.A) && api.isReady(GatewaySide.B));

      assertThrows(
          IllegalArgumentException.class,
          () -> api.newLimit("invalid.id", 501, 25_000_000_000L, 10, -2, true, 0, 0, 1, 0, 0, 0));
      assertThrows(
          IllegalArgumentException.class,
          () -> api.amend("invalid.id", 501, 25_000_000_000L, 10, -2, true, 0, 0));
      assertThrows(IllegalArgumentException.class, () -> api.cancel("invalid.id", 501));

      String externalClientOrderId = "1-ab-BTC-PERPETUAL";
      long numericClientOrderId = new ClientOrderIdMap().map(externalClientOrderId);
      long newCorrelation =
          api.newMarket(externalClientOrderId, 501, 10, -2, true, 0, 0, 1, 0, 0, 0);
      await(() -> sideA.outboundCount() == 2);
      assertEquals(
          numericClientOrderId,
          NewOrderRequestDecoder.clientOrderId(sideA.outboundFrame(1), 0));

      long signedOrderId = Long.MIN_VALUE + 7;
      sideA.enqueue(
          newOrderResponse(
              2, 2, numericClientOrderId, newCorrelation, signedOrderId, 501, 10, 0));
      await(() -> api.orderStateByOrderId(signedOrderId) == LocalOrderStateStore.STATE_OPEN);

      long cancelCorrelation = api.cancelByOrderId(signedOrderId);
      await(() -> sideA.outboundCount() == 3);
      ByteBuffer request = sideA.outboundFrame(2);
      CancelOrderByIdRequestDecoder.validate(request, 0);
      assertEquals(signedOrderId, CancelOrderByIdRequestDecoder.orderId(request, 0));
      assertEquals(cancelCorrelation, CancelOrderByIdRequestDecoder.correlationId(request, 0));
      assertEquals(501L, CancelOrderByIdRequestDecoder.instrumentId(request, 0));

      sideA.enqueue(
          cancelOrderResponse(
              3,
              3,
              numericClientOrderId,
              cancelCorrelation,
              signedOrderId,
              501));
      await(
          () ->
              api.orderStateByOrderId(signedOrderId)
                  == LocalOrderStateStore.STATE_CANCELED);
    }
  }

  public void testSequenceGapClosesReadinessAndRequestsExactResendBeforeRecovery()
      throws Exception {
    MutableClock clock = new MutableClock();
    ScriptedTransport sideA = new ScriptedTransport();
    ScriptedTransport sideB = new ScriptedTransport();
    OpenOrderRecoveryCache recovery =
        new OpenOrderRecoveryCache(
            clock, OpenOrderRecoveryCache.MINIMUM_REFRESH_INTERVAL, List::of);
    try (StarbaseCredentials credentialsA = credentials('a');
        StarbaseCredentials credentialsB = credentials('b');
        StarbaseOrderEntryApi api =
            pairedApi(clock, credentialsA, credentialsB, recovery, sideA, sideB)) {
      api.setReferenceDataReady(true);
      api.start();
      await(() -> sideA.outboundCount() == 1 && sideB.outboundCount() == 1);
      sideA.enqueue(logonConfirmation(1, 1));
      sideB.enqueue(logonConfirmation(1, 1));
      await(api::isReady);

      sideA.enqueue(heartbeat(3, 1, false));
      await(() -> sideA.outboundCount() == 2);
      assertFalse(api.isReady());
      ByteBuffer resendRequest = sideA.outboundFrame(1);
      ResendRequestCodec.validate(resendRequest, 0);
      assertEquals(2L, ResendRequestCodec.fromSequenceNumber(resendRequest, 0));
      assertEquals(0L, ResendRequestCodec.toSequenceNumber(resendRequest, 0));

      sideA.enqueue(heartbeat(2, 2, true));
      sideA.enqueue(heartbeat(3, 2, true));
      await(api::isReady);
    }
  }

  public void testDisconnectClosesPeerRoutingUntilAFreshExactSnapshotRestoresIt()
      throws Exception {
    MutableClock clock = new MutableClock();
    ScriptedTransport firstA = new ScriptedTransport();
    ScriptedTransport secondA = new ScriptedTransport();
    ScriptedTransport sideB = new ScriptedTransport();
    ScriptedTransportFactory transports =
        new ScriptedTransportFactory(firstA, secondA, sideB);
    AtomicInteger loads = new AtomicInteger();
    OpenOrderRecoveryCache recovery =
        new OpenOrderRecoveryCache(
            clock,
            OpenOrderRecoveryCache.MINIMUM_REFRESH_INTERVAL,
            () -> loads.incrementAndGet() == 1 ? List.of() : List.of(openOrder(9_001)));
    try (StarbaseCredentials credentialsA = credentials('a');
        StarbaseCredentials credentialsB = credentials('b');
        StarbaseOrderEntryApi api =
            new StarbaseOrderEntryApi(
                context(clock, GatewaySide.A),
                credentialsA,
                context(clock, GatewaySide.B),
                credentialsB,
                recovery,
                transports)) {
      api.setReferenceDataReady(true);
      api.start();
      await(() -> firstA.outboundCount() == 1 && sideB.outboundCount() == 1);
      firstA.enqueue(logonConfirmation(1, 1));
      sideB.enqueue(logonConfirmation(1, 1));
      await(() -> api.isReady(GatewaySide.A) && api.isReady(GatewaySide.B));
      long correlationId =
          api.newLimit(101, 501, 25_000_000_000L, 10, -2, true, 0, 0, 1, 0, 0, 0);
      await(() -> firstA.outboundCount() == 2);
      firstA.enqueue(newOrderResponse(2, 2, 101, correlationId, 9_001, 501, 10, 0));
      await(() -> api.orderStateByOrderId(9_001) == LocalOrderStateStore.STATE_OPEN);

      firstA.endInput();
      await(() -> !api.isReady());
      assertEquals(1, loads.get());

      clock.now = Duration.ofMinutes(1).toNanos();
      await(() -> secondA.outboundCount() == 1);
      ByteBuffer reconnectLogon = secondA.outboundFrame(0);
      LogonDecoder.validate(reconnectLogon, 0);
      assertEquals(0, LogonDecoder.resetSequenceNumber(reconnectLogon, 0));
      assertEquals(3L, TcpHeaderCodec.sequenceNumber(reconnectLogon, 0));
      assertEquals(2L, TcpHeaderCodec.lastProcessedSequenceNumber(reconnectLogon, 0));
      await(() -> api.isReady(GatewaySide.B));
      assertEquals(2, loads.get());
      int beforePeerOrder = sideB.outboundTemplateCount(100);
      api.newMarket(102, 501, 5, -2, true, 0, 0, -1, 0, 0, 0);
      await(() -> sideB.outboundTemplateCount(100) == beforePeerOrder + 1);
    }
  }

  public void testFailedRestOnlyAndDuplicateSnapshotsKeepPublicReadinessClosed()
      throws Exception {
    assertSnapshotNeverReady(() -> { throw new IllegalStateException("REST unavailable"); });
    assertSnapshotNeverReady(() -> List.of(openOrder(7_001)));
    assertSnapshotNeverReady(() -> List.of(openOrder(7_001), openOrder(7_001)));
  }

  public void testFillAmendCancelAndMassCancelStayOnTheOriginSession() throws Exception {
    MutableClock clock = new MutableClock();
    ScriptedTransport sideA = new ScriptedTransport();
    ScriptedTransport sideB = new ScriptedTransport();
    OpenOrderRecoveryCache recovery =
        new OpenOrderRecoveryCache(
            clock, OpenOrderRecoveryCache.MINIMUM_REFRESH_INTERVAL, List::of);
    long[] fillEvent = new long[2];
    try (StarbaseCredentials credentialsA = credentials('a');
        StarbaseCredentials credentialsB = credentials('b');
        StarbaseOrderEntryApi api =
            pairedApi(clock, credentialsA, credentialsB, recovery, sideA, sideB)) {
      api.getFillsChannel()
          .addListener(
              (key, value, timestamp) -> {
                fillEvent[0] = key;
                fillEvent[1] = value;
              });
      api.setReferenceDataReady(true);
      api.start();
      await(() -> sideA.outboundCount() == 1 && sideB.outboundCount() == 1);
      sideA.enqueue(logonConfirmation(1, 1));
      sideB.enqueue(logonConfirmation(1, 1));
      await(() -> api.isReady(GatewaySide.A) && api.isReady(GatewaySide.B));

      long newCorrelation =
          api.newLimit(101, 501, 25_000_000_000L, 10, -2, true, 0, 0, 1, 0, 0, 0);
      await(() -> sideA.outboundCount() == 2);
      sideA.enqueue(newOrderResponse(2, 2, 101, newCorrelation, 9_001, 501, 10, 0));
      await(() -> api.orderStateByOrderId(9_001) == LocalOrderStateStore.STATE_OPEN);

      sideA.enqueue(orderFilled(3, 2, 101, 9_001, 501, 8_001, 3, 3));
      await(() -> api.remainingQuantity(9_001) == 7 && fillEvent[0] == 9_001);
      assertEquals(LocalOrderStateStore.STATE_PARTIALLY_FILLED, api.orderStateByOrderId(9_001));
      assertEquals(9_001L, fillEvent[0]);
      assertEquals(8_001L, fillEvent[1]);
      sideA.enqueue(orderFilled(4, 2, 101, 9_001, 501, 8_001, 3, 3));
      await(() -> api.isReady(GatewaySide.A));
      assertEquals(7L, api.remainingQuantity(9_001));

      long amendCorrelation = api.amend(101, 501, 26_000_000_000L, 12, -2, true, 0, 0);
      await(() -> sideA.outboundCount() == 3);
      AmendOrderRequestDecoder.validate(sideA.outboundFrame(2), 0);
      assertEquals(amendCorrelation, AmendOrderRequestDecoder.correlationId(sideA.outboundFrame(2), 0));
      sideA.enqueue(
          amendOrderResponse(
              5, 3, 101, amendCorrelation, 9_001, 501, 12, 5, 8_002, 2));
      await(() -> api.remainingQuantity(9_001) == 7 && fillEvent[1] == 8_002);

      long cancelCorrelation = api.cancel(101, 501);
      await(() -> sideA.outboundCount() == 4);
      CancelOrderRequestDecoder.validate(sideA.outboundFrame(3), 0);
      assertEquals(cancelCorrelation, CancelOrderRequestDecoder.correlationId(sideA.outboundFrame(3), 0));
      sideA.enqueue(cancelOrderResponse(6, 4, 101, cancelCorrelation, 9_001, 501));
      await(() -> api.orderStateByOrderId(9_001) == LocalOrderStateStore.STATE_CANCELED);

      long massCorrelation = api.massCancel(Long.MIN_VALUE, 501, 0, 0);
      await(() -> sideA.outboundCount() == 5);
      MassCancelRequestDecoder.validate(sideA.outboundFrame(4), 0);
      assertEquals(massCorrelation, MassCancelRequestDecoder.correlationId(sideA.outboundFrame(4), 0));
      sideA.enqueue(massCancelResponse(7, 5, massCorrelation, 1));
      await(() -> api.correlationState(massCorrelation) == 2);
      assertEquals(1, sideB.outboundCount());
    }
  }

  public void testImmediateTerminalFillAndQueuedPlacedCancellationLifecycle()
      throws Exception {
    MutableClock clock = new MutableClock();
    ScriptedTransport sideA = new ScriptedTransport();
    ScriptedTransport sideB = new ScriptedTransport();
    OpenOrderRecoveryCache recovery =
        new OpenOrderRecoveryCache(
            clock, OpenOrderRecoveryCache.MINIMUM_REFRESH_INTERVAL, List::of);
    try (StarbaseCredentials credentialsA = credentials('a');
        StarbaseCredentials credentialsB = credentials('b');
        StarbaseOrderEntryApi api =
            pairedApi(clock, credentialsA, credentialsB, recovery, sideA, sideB)) {
      api.setReferenceDataReady(true);
      api.start();
      await(() -> sideA.outboundCount() == 1 && sideB.outboundCount() == 1);
      sideA.enqueue(logonConfirmation(1, 1));
      sideB.enqueue(logonConfirmation(1, 1));
      await(() -> api.isReady(GatewaySide.A) && api.isReady(GatewaySide.B));

      long filledCorrelation =
          api.newMarket(201, 501, 10, -2, true, 0, 0, 1, 0, 0, 0);
      await(() -> sideA.outboundCount() == 2);
      sideA.enqueue(
          newOrderResponseOutcome(
              2, 2, 201, filledCorrelation, 9_101, 501, 10, 2, 8_101, 10));
      await(() -> api.orderStateByOrderId(9_101) == LocalOrderStateStore.STATE_FILLED);
      assertEquals(0L, api.remainingQuantity(9_101));

      long queuedCorrelation =
          api.newLimit(202, 501, 26_000_000_000L, 12, -2, true, 0, 0, -1, 0, 0, 0);
      await(() -> sideA.outboundCount() == 3);
      sideA.enqueue(
          newOrderResponseOutcome(
              3, 3, 202, queuedCorrelation, 9_102, 501, 12, 4, 0, 0));
      await(() -> api.orderStateByOrderId(9_102) == LocalOrderStateStore.STATE_QUEUED);
      sideA.enqueue(orderPlaced(4, 3, 202, queuedCorrelation, 9_102, 501, 12));
      await(() -> api.orderStateByOrderId(9_102) == LocalOrderStateStore.STATE_OPEN);
      sideA.enqueue(ordersCanceled(5, 3, 202, 9_102, 501, 0));
      await(() -> api.orderStateByOrderId(9_102) == LocalOrderStateStore.STATE_CANCELED);
      assertTrue(api.isReady(GatewaySide.A));
    }
  }

  public void testCommandRejectsCompleteCorrelationsWithoutInventingStateChanges()
      throws Exception {
    MutableClock clock = new MutableClock();
    ScriptedTransport sideA = new ScriptedTransport();
    ScriptedTransport sideB = new ScriptedTransport();
    OpenOrderRecoveryCache recovery =
        new OpenOrderRecoveryCache(
            clock, OpenOrderRecoveryCache.MINIMUM_REFRESH_INTERVAL, List::of);
    try (StarbaseCredentials credentialsA = credentials('a');
        StarbaseCredentials credentialsB = credentials('b');
        StarbaseOrderEntryApi api =
            pairedApi(clock, credentialsA, credentialsB, recovery, sideA, sideB)) {
      api.setReferenceDataReady(true);
      api.start();
      await(() -> sideA.outboundCount() == 1 && sideB.outboundCount() == 1);
      sideA.enqueue(logonConfirmation(1, 1));
      sideB.enqueue(logonConfirmation(1, 1));
      await(() -> api.isReady(GatewaySide.A) && api.isReady(GatewaySide.B));
      long newCorrelation =
          api.newLimit(301, 501, 25_000_000_000L, 10, -2, true, 0, 0, 1, 0, 0, 0);
      await(() -> sideA.outboundCount() == 2);
      sideA.enqueue(newOrderResponse(2, 2, 301, newCorrelation, 9_201, 501, 10, 0));
      await(() -> api.orderStateByOrderId(9_201) == LocalOrderStateStore.STATE_OPEN);

      long amend = api.amend(301, 501, 26_000_000_000L, 10, -2, true, 0, 0);
      await(() -> sideA.outboundCount() == 3);
      sideA.enqueue(amendReject(3, 3, 301, amend, 9_201, 501, 30));
      await(() -> api.correlationState(amend) == 2);
      assertEquals(30, api.correlationResultCode(amend));
      assertEquals(LocalOrderStateStore.STATE_OPEN, api.orderStateByOrderId(9_201));

      long cancel = api.cancel(301, 501);
      await(() -> sideA.outboundCount() == 4);
      sideA.enqueue(cancelReject(4, 4, 301, cancel, 9_201, 501, 8));
      await(() -> api.correlationState(cancel) == 2);
      assertEquals(8, api.correlationResultCode(cancel));
      assertEquals(LocalOrderStateStore.STATE_OPEN, api.orderStateByOrderId(9_201));

      long mass = api.massCancel(Long.MIN_VALUE, 501, 0, 0);
      await(() -> sideA.outboundCount() == 5);
      sideA.enqueue(massCancelReject(5, 5, mass, 3));
      await(() -> api.correlationState(mass) == 2);
      assertEquals(3, api.correlationResultCode(mass));

      long rejectedNew =
          api.newMarket(302, 501, 5, -2, true, 0, 0, -1, 0, 0, 0);
      await(() -> sideA.outboundCount() == 6);
      sideA.enqueue(newOrderReject(6, 6, 302, rejectedNew, 9_202, 501, 29));
      await(
          () ->
              api.orderStateByClientOrderId(302) == LocalOrderStateStore.STATE_REJECTED
                  && api.correlationState(rejectedNew) == 2);
      assertEquals(29, api.correlationResultCode(rejectedNew));
      assertTrue(api.isReady(GatewaySide.A));
    }
  }

  public void testResponseQuantityExponentMismatchFailsClosed() throws Exception {
    MutableClock clock = new MutableClock();
    ScriptedTransport sideA = new ScriptedTransport();
    ScriptedTransport sideB = new ScriptedTransport();
    OpenOrderRecoveryCache recovery =
        new OpenOrderRecoveryCache(
            clock, OpenOrderRecoveryCache.MINIMUM_REFRESH_INTERVAL, List::of);
    try (StarbaseCredentials credentialsA = credentials('a');
        StarbaseCredentials credentialsB = credentials('b');
        StarbaseOrderEntryApi api =
            pairedApi(clock, credentialsA, credentialsB, recovery, sideA, sideB)) {
      api.setReferenceDataReady(true);
      api.start();
      await(() -> sideA.outboundCount() == 1 && sideB.outboundCount() == 1);
      sideA.enqueue(logonConfirmation(1, 1));
      sideB.enqueue(logonConfirmation(1, 1));
      await(() -> api.isReady(GatewaySide.A) && api.isReady(GatewaySide.B));
      long correlation =
          api.newLimit(401, 501, 25_000_000_000L, 10, -2, true, 0, 0, 1, 0, 0, 0);
      await(() -> sideA.outboundCount() == 2);
      ByteBuffer mismatched =
          newOrderResponse(2, 2, 401, correlation, 9_301, 501, 10, 0);
      int body = TcpHeaderCodec.ENCODED_LENGTH;
      Decimal72Codec.put(mismatched, body + 56, 10, -3);
      Decimal72Codec.put(mismatched, body + 65, 0, -3);
      Decimal72Codec.put(mismatched, body + 74, 10, -3);
      sideA.enqueue(mismatched);
      await(() -> !api.isReady());
      assertEquals(LocalOrderStateStore.STATE_PENDING, api.orderStateByClientOrderId(401));
    }
  }

  public void testIdleOrderFlowStaysOpenWithHeartbeatsUntilExactPeerInactivityBoundary()
      throws Exception {
    MutableClock clock = new MutableClock();
    ScriptedTransport sideA = new ScriptedTransport();
    ScriptedTransport sideB = new ScriptedTransport();
    OpenOrderRecoveryCache recovery =
        new OpenOrderRecoveryCache(
            clock, OpenOrderRecoveryCache.MINIMUM_REFRESH_INTERVAL, List::of);
    try (StarbaseCredentials credentialsA = credentials('a');
        StarbaseCredentials credentialsB = credentials('b');
        StarbaseOrderEntryApi api =
            pairedApi(clock, credentialsA, credentialsB, recovery, sideA, sideB)) {
      api.setReferenceDataReady(true);
      api.start();
      await(() -> sideA.outboundCount() == 1 && sideB.outboundCount() == 1);
      sideA.enqueue(logonConfirmation(1, 1));
      sideB.enqueue(logonConfirmation(1, 1));
      await(() -> api.isReady(GatewaySide.A) && api.isReady(GatewaySide.B));

      clock.now = Duration.ofSeconds(1).toNanos();
      await(
          () ->
              sideA.outboundTemplateCount(HeartbeatCodec.TEMPLATE_ID) == 1
                  && sideB.outboundTemplateCount(HeartbeatCodec.TEMPLATE_ID) == 1);
      sideA.enqueue(heartbeat(2, 2, false));
      sideB.enqueue(heartbeat(2, 2, false));
      await(() -> api.isReady(GatewaySide.A) && api.isReady(GatewaySide.B));

      clock.now = Duration.ofSeconds(119).toNanos();
      await(
          () ->
              sideA.outboundTemplateCount(HeartbeatCodec.TEMPLATE_ID) >= 2
                  && sideB.outboundTemplateCount(HeartbeatCodec.TEMPLATE_ID) >= 2);
      assertTrue(api.isReady(), "no order listeners or commands must not close an idle session");
      clock.now = Duration.ofSeconds(121).toNanos();
      await(() -> !api.isReady());
    }
  }

  public void testAmbiguousOriginWriteNeverRetriesThePeerAndLeavesRecoveryClosed()
      throws Exception {
    MutableClock clock = new MutableClock();
    ScriptedTransport sideA = new ScriptedTransport();
    ScriptedTransport sideB = new ScriptedTransport();
    OpenOrderRecoveryCache recovery =
        new OpenOrderRecoveryCache(
            clock, OpenOrderRecoveryCache.MINIMUM_REFRESH_INTERVAL, List::of);
    try (StarbaseCredentials credentialsA = credentials('a');
        StarbaseCredentials credentialsB = credentials('b');
        StarbaseOrderEntryApi api =
            pairedApi(clock, credentialsA, credentialsB, recovery, sideA, sideB)) {
      api.setReferenceDataReady(true);
      api.start();
      await(() -> sideA.outboundCount() == 1 && sideB.outboundCount() == 1);
      sideA.enqueue(logonConfirmation(1, 1));
      sideB.enqueue(logonConfirmation(1, 1));
      await(() -> api.isReady(GatewaySide.A) && api.isReady(GatewaySide.B));
      sideA.failNextWrite();

      assertThrows(
          IllegalStateException.class,
          () ->
              api.newMarket(501, 501, 5, -2, true, 0, 0, 1, 0, 0, 0));
      assertEquals(1, sideB.outboundCount());
      assertEquals(LocalOrderStateStore.STATE_PENDING, api.orderStateByClientOrderId(501));
      await(() -> !api.isReady());
      assertEquals(1, sideB.outboundCount());
    }
  }

  public void testWarmedPublicNewOrderRoutingAllocatesNothingOnTheCallerHotPath()
      throws Exception {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    MutableClock clock = new MutableClock();
    ScriptedTransport sideA = new ScriptedTransport();
    ScriptedTransport sideB = new ScriptedTransport();
    OpenOrderRecoveryCache recovery =
        new OpenOrderRecoveryCache(
            clock, OpenOrderRecoveryCache.MINIMUM_REFRESH_INTERVAL, List::of);
    try (StarbaseCredentials credentialsA = credentials('a');
        StarbaseCredentials credentialsB = credentials('b');
        StarbaseOrderEntryApi api =
            pairedApi(clock, credentialsA, credentialsB, recovery, sideA, sideB)) {
      api.setReferenceDataReady(true);
      api.start();
      await(() -> sideA.outboundCount() == 1 && sideB.outboundCount() == 1);
      sideA.enqueue(logonConfirmation(1, 1));
      sideB.enqueue(logonConfirmation(1, 1));
      await(() -> api.isReady(GatewaySide.A) && api.isReady(GatewaySide.B));
      sideA.discardWrites();
      for (int iteration = 0; iteration < 3_000; iteration++) {
        api.newMarket(10_000L + iteration, 501, 5, -2, true, 0, 0, 1, 0, 0, 0);
      }
      long threadId = Thread.currentThread().threadId();
      long before = bean.getThreadAllocatedBytes(threadId);
      for (int iteration = 0; iteration < 1_000; iteration++) {
        api.newMarket(20_000L + iteration, 501, 5, -2, true, 0, 0, 1, 0, 0, 0);
      }
      assertEquals(
          0L,
          bean.getThreadAllocatedBytes(threadId) - before,
          "public new-order routing hot path allocated bytes");
    }
  }

  private static void assertSnapshotNeverReady(
      io.contek.invoker.deribit.starbase.rest.OpenOrderSnapshotLoader loader)
      throws Exception {
    MutableClock clock = new MutableClock();
    ScriptedTransport sideA = new ScriptedTransport();
    ScriptedTransport sideB = new ScriptedTransport();
    OpenOrderRecoveryCache recovery =
        new OpenOrderRecoveryCache(
            clock, OpenOrderRecoveryCache.MINIMUM_REFRESH_INTERVAL, loader);
    try (StarbaseCredentials credentialsA = credentials('a');
        StarbaseCredentials credentialsB = credentials('b');
        StarbaseOrderEntryApi api =
            pairedApi(clock, credentialsA, credentialsB, recovery, sideA, sideB)) {
      api.setReferenceDataReady(true);
      api.start();
      await(() -> sideA.outboundCount() == 1 && sideB.outboundCount() == 1);
      sideA.enqueue(logonConfirmation(1, 1));
      sideB.enqueue(logonConfirmation(1, 1));
      await(
          () ->
              api.isAuthenticated(GatewaySide.A)
                  && api.isAuthenticated(GatewaySide.B));
      assertFalse(api.isReady());
    }
  }

  private static StarbaseOrderEntryApi pairedApi(
      MutableClock clock,
      StarbaseCredentials credentialsA,
      StarbaseCredentials credentialsB,
      OpenOrderRecoveryCache recovery,
      ScriptedTransport sideA,
      ScriptedTransport sideB) {
    Function<StarbaseOrderEntryContext, OrderEntryDuplexTransport> transports =
        context -> context.gatewaySide() == GatewaySide.A ? sideA : sideB;
    return new StarbaseOrderEntryApi(
        context(clock, GatewaySide.A),
        credentialsA,
        context(clock, GatewaySide.B),
        credentialsB,
        recovery,
        transports);
  }

  private static StarbaseOrderEntryContext context(MutableClock clock, GatewaySide side) {
    return new StarbaseOrderEntryContext(
        new InetSocketAddress("127.0.0.1", side == GatewaySide.A ? 4210 : 4211),
        ProductGroup.BTC,
        side,
        Duration.ofSeconds(1),
        Duration.ofMinutes(2),
        4096,
        4096,
        IoPolicy.BLOCKING,
        clock);
  }

  private static StarbaseCredentials credentials(char suffix) {
    return new StarbaseCredentials(new char[] {'c', suffix}, new char[] {'s', suffix});
  }

  private static void assertLogon(ByteBuffer frame) {
    LogonDecoder.validate(frame, 0);
    assertEquals(15, LogonDecoder.schemaVersion(frame, 0));
    assertEquals(1, LogonDecoder.resetSequenceNumber(frame, 0));
  }

  private static ByteBuffer logonConfirmation(long sequence, long acknowledgment) {
    ByteBuffer frame = frame(40);
    LogonConfirmationCodec.encode(frame, 0, 1, 15, sequence, acknowledgment, 1);
    frame.putShort(TcpHeaderCodec.VERSION_OFFSET, (short) 12);
    return frame;
  }

  private static ByteBuffer heartbeat(long sequence, long acknowledgment, boolean resend) {
    ByteBuffer frame = frame(40);
    HeartbeatCodec.encode(frame, 0, 0, sequence, acknowledgment, 1);
    frame.putShort(TcpHeaderCodec.VERSION_OFFSET, (short) 0);
    if (resend) {
      frame.put(TcpHeaderCodec.FLAGS_OFFSET, (byte) TcpHeaderCodec.FLAG_RESEND);
    }
    return frame;
  }

  private static ByteBuffer newOrderResponse(
      long sequence,
      long acknowledgment,
      long clientOrderId,
      long correlationId,
      long orderId,
      long instrumentId,
      long quantity,
      long totalFilled) {
    return newOrderResponseOutcome(
        sequence,
        acknowledgment,
        clientOrderId,
        correlationId,
        orderId,
        instrumentId,
        quantity,
        1,
        0,
        totalFilled);
  }

  private static ByteBuffer newOrderResponseOutcome(
      long sequence,
      long acknowledgment,
      long clientOrderId,
      long correlationId,
      long orderId,
      long instrumentId,
      long quantity,
      int status,
      long matchId,
      long fillQuantity) {
    int fillCount = fillQuantity == 0 ? 0 : 1;
    int messageLength = 134 + fillCount * 25;
    ByteBuffer frame = frame((messageLength + 7) & ~7);
    TcpHeaderCodec.encode(
        frame, 0, 0, messageLength, 200, 5, sequence, acknowledgment, 12_345);
    int body = TcpHeaderCodec.ENCODED_LENGTH;
    frame.putLong(body, 12_345);
    frame.putLong(body + 8, 7_001);
    frame.putLong(body + 16, clientOrderId);
    frame.putLong(body + 24, correlationId);
    frame.putLong(body + 32, orderId);
    frame.putLong(body + 40, instrumentId);
    frame.putLong(body + 48, 25_000_000_000L);
    Decimal72Codec.put(frame, body + 56, quantity, -2);
    Decimal72Codec.put(frame, body + 65, fillQuantity, -2);
    Decimal72Codec.put(frame, body + 74, quantity - fillQuantity, -2);
    frame.putLong(body + 83, 12_344);
    frame.put(body + 91, (byte) 1);
    frame.put(body + 92, (byte) status);
    frame.put(body + 93, (byte) 0);
    int fills = body + 94;
    frame.putShort(fills, (short) 25);
    frame.putShort(fills + 2, (short) fillCount);
    int legs = fills + 4;
    if (fillCount != 0) {
      frame.putLong(legs, matchId);
      frame.putLong(legs + 8, 25_000_000_000L);
      Decimal72Codec.put(frame, legs + 16, fillQuantity, -2);
      legs += 25;
    }
    frame.putShort(legs, (short) 34);
    frame.putShort(legs + 2, (short) 0);
    TcpHeaderCodec.zeroPadding(frame, 0, messageLength);
    return frame;
  }

  private static ByteBuffer orderPlaced(
      long sequence,
      long acknowledgment,
      long clientOrderId,
      long correlationId,
      long orderId,
      long instrumentId,
      long quantity) {
    ByteBuffer frame = frame(128);
    TcpHeaderCodec.encode(frame, 0, 0, 128, 312, 8, sequence, acknowledgment, 17_000);
    int body = TcpHeaderCodec.ENCODED_LENGTH;
    frame.putLong(body, 17_000);
    frame.putLong(body + 8, 7_500);
    frame.putLong(body + 16, clientOrderId);
    frame.putLong(body + 24, orderId);
    frame.putLong(body + 32, instrumentId);
    frame.putLong(body + 40, 26_000_000_000L);
    Decimal72Codec.put(frame, body + 48, quantity, -2);
    Decimal72Codec.put(frame, body + 57, 0, -2);
    Decimal72Codec.put(frame, body + 66, quantity, -2);
    frame.put(body + 75, (byte) 1);
    frame.put(body + 76, (byte) 0);
    frame.put(body + 77, (byte) 0);
    frame.put(body + 78, (byte) 0);
    frame.put(body + 79, (byte) 0);
    frame.putLong(body + 80, correlationId);
    frame.putShort(body + 88, (short) 25);
    frame.putShort(body + 90, (short) 0);
    frame.putShort(body + 92, (short) 34);
    frame.putShort(body + 94, (short) 0);
    return frame;
  }

  private static ByteBuffer ordersCanceled(
      long sequence,
      long acknowledgment,
      long clientOrderId,
      long orderId,
      long instrumentId,
      long totalFilled) {
    ByteBuffer frame = frame(88);
    TcpHeaderCodec.encode(frame, 0, 0, 88, 310, 5, sequence, acknowledgment, 18_000);
    int body = TcpHeaderCodec.ENCODED_LENGTH;
    frame.putLong(body, 18_000);
    frame.putLong(body + 8, 7_600);
    frame.put(body + 16, (byte) 1);
    int dimensions = body + 17;
    frame.putShort(dimensions, (short) 35);
    frame.putShort(dimensions + 2, (short) 1);
    int order = dimensions + 4;
    frame.putLong(order, clientOrderId);
    frame.putLong(order + 8, orderId);
    frame.putLong(order + 16, instrumentId);
    Decimal72Codec.put(frame, order + 24, totalFilled, -2);
    frame.put(order + 33, (byte) 3);
    frame.put(order + 34, (byte) 0);
    return frame;
  }

  private static ByteBuffer orderFilled(
      long sequence,
      long acknowledgment,
      long clientOrderId,
      long orderId,
      long instrumentId,
      long matchId,
      long fillQuantity,
      long totalFilled) {
    ByteBuffer frame = frame(120);
    TcpHeaderCodec.encode(frame, 0, 0, 116, 300, 0, sequence, acknowledgment, 13_000);
    int body = TcpHeaderCodec.ENCODED_LENGTH;
    frame.putLong(body, 13_000);
    frame.putLong(body + 8, 7_100);
    int dimensions = body + 16;
    frame.putShort(dimensions, (short) 60);
    frame.putShort(dimensions + 2, (short) 1);
    int fill = dimensions + 4;
    frame.putLong(fill, clientOrderId);
    frame.putLong(fill + 8, orderId);
    frame.putLong(fill + 16, instrumentId);
    frame.putLong(fill + 24, matchId);
    frame.putLong(fill + 32, 25_000_000_000L);
    Decimal72Codec.put(frame, fill + 40, fillQuantity, -2);
    Decimal72Codec.put(frame, fill + 49, totalFilled, -2);
    frame.put(fill + 58, (byte) 1);
    frame.put(fill + 59, (byte) 0);
    int legs = fill + 60;
    frame.putShort(legs, (short) 34);
    frame.putShort(legs + 2, (short) 0);
    TcpHeaderCodec.zeroPadding(frame, 0, 116);
    return frame;
  }

  private static ByteBuffer amendOrderResponse(
      long sequence,
      long acknowledgment,
      long clientOrderId,
      long correlationId,
      long orderId,
      long instrumentId,
      long quantity,
      long totalFilled,
      long matchId,
      long immediateFill) {
    int fillCount = immediateFill == 0 ? 0 : 1;
    int messageLength = 133 + fillCount * 25;
    ByteBuffer frame = frame((messageLength + 7) & ~7);
    TcpHeaderCodec.encode(
        frame, 0, 0, messageLength, 210, 5, sequence, acknowledgment, 14_000);
    int body = TcpHeaderCodec.ENCODED_LENGTH;
    frame.putLong(body, 14_000);
    frame.putLong(body + 8, 7_200);
    frame.putLong(body + 16, clientOrderId);
    frame.putLong(body + 24, correlationId);
    frame.putLong(body + 32, orderId);
    frame.putLong(body + 40, instrumentId);
    frame.putLong(body + 48, 26_000_000_000L);
    Decimal72Codec.put(frame, body + 56, quantity, -2);
    Decimal72Codec.put(frame, body + 65, totalFilled, -2);
    Decimal72Codec.put(frame, body + 74, quantity - totalFilled, -2);
    frame.putLong(body + 83, 13_999);
    frame.put(body + 91, (byte) 1);
    frame.put(body + 92, (byte) 0);
    int fills = body + 93;
    frame.putShort(fills, (short) 25);
    frame.putShort(fills + 2, (short) fillCount);
    int legs = fills + 4;
    if (fillCount != 0) {
      frame.putLong(legs, matchId);
      frame.putLong(legs + 8, 26_000_000_000L);
      Decimal72Codec.put(frame, legs + 16, immediateFill, -2);
      legs += 25;
    }
    frame.putShort(legs, (short) 34);
    frame.putShort(legs + 2, (short) 0);
    TcpHeaderCodec.zeroPadding(frame, 0, messageLength);
    return frame;
  }

  private static ByteBuffer cancelOrderResponse(
      long sequence,
      long acknowledgment,
      long clientOrderId,
      long correlationId,
      long orderId,
      long instrumentId) {
    ByteBuffer frame = frame(88);
    TcpHeaderCodec.encode(frame, 0, 0, 88, 220, 0, sequence, acknowledgment, 15_000);
    int body = TcpHeaderCodec.ENCODED_LENGTH;
    frame.putLong(body, 15_000);
    frame.putLong(body + 8, 7_300);
    frame.putLong(body + 16, clientOrderId);
    frame.putLong(body + 24, correlationId);
    frame.putLong(body + 32, orderId);
    frame.putLong(body + 40, instrumentId);
    frame.putLong(body + 48, 14_999);
    return frame;
  }

  private static ByteBuffer massCancelResponse(
      long sequence, long acknowledgment, long correlationId, int count) {
    ByteBuffer frame = frame(72);
    TcpHeaderCodec.encode(frame, 0, 0, 68, 240, 0, sequence, acknowledgment, 16_000);
    int body = TcpHeaderCodec.ENCODED_LENGTH;
    frame.putLong(body, 16_000);
    frame.putLong(body + 8, 7_400);
    frame.putLong(body + 16, correlationId);
    frame.putLong(body + 24, 15_999);
    frame.putInt(body + 32, count);
    TcpHeaderCodec.zeroPadding(frame, 0, 68);
    return frame;
  }

  private static ByteBuffer amendReject(
      long sequence,
      long acknowledgment,
      long clientOrderId,
      long correlationId,
      long orderId,
      long instrumentId,
      int reason) {
    return orderReject(
        sequence, acknowledgment, 212, clientOrderId, correlationId, orderId, instrumentId, reason);
  }

  private static ByteBuffer cancelReject(
      long sequence,
      long acknowledgment,
      long clientOrderId,
      long correlationId,
      long orderId,
      long instrumentId,
      int reason) {
    return orderReject(
        sequence, acknowledgment, 222, clientOrderId, correlationId, orderId, instrumentId, reason);
  }

  private static ByteBuffer newOrderReject(
      long sequence,
      long acknowledgment,
      long clientOrderId,
      long correlationId,
      long orderId,
      long instrumentId,
      int reason) {
    return orderReject(
        sequence, acknowledgment, 202, clientOrderId, correlationId, orderId, instrumentId, reason);
  }

  private static ByteBuffer orderReject(
      long sequence,
      long acknowledgment,
      int templateId,
      long clientOrderId,
      long correlationId,
      long orderId,
      long instrumentId,
      int reason) {
    ByteBuffer frame = frame(88);
    int version = templateId == 222 ? 9 : 15;
    TcpHeaderCodec.encode(
        frame, 0, 0, 82, templateId, version, sequence, acknowledgment, 19_000);
    int body = TcpHeaderCodec.ENCODED_LENGTH;
    frame.putLong(body, 19_000);
    frame.putLong(body + 8, 7_700);
    frame.putLong(body + 16, clientOrderId);
    frame.putLong(body + 24, correlationId);
    frame.putLong(body + 32, orderId);
    frame.putLong(body + 40, instrumentId);
    frame.put(body + 48, (byte) reason);
    frame.put(body + 49, (byte) 0);
    TcpHeaderCodec.zeroPadding(frame, 0, 82);
    return frame;
  }

  private static ByteBuffer massCancelReject(
      long sequence, long acknowledgment, long correlationId, int reason) {
    ByteBuffer frame = frame(64);
    TcpHeaderCodec.encode(frame, 0, 0, 58, 242, 0, sequence, acknowledgment, 20_000);
    int body = TcpHeaderCodec.ENCODED_LENGTH;
    frame.putLong(body, 20_000);
    frame.putLong(body + 8, 7_800);
    frame.putLong(body + 16, correlationId);
    frame.put(body + 24, (byte) reason);
    frame.put(body + 25, (byte) 0);
    TcpHeaderCodec.zeroPadding(frame, 0, 58);
    return frame;
  }

  private static ByteBuffer frame(int capacity) {
    return ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
  }

  private static StarbaseOpenOrder openOrder(long orderId) {
    return new StarbaseOpenOrder(
        orderId,
        "BTC-PERPETUAL",
        StarbaseOrderSide.BUY,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        StarbaseRestOrderState.OPEN,
        StarbaseRestOrderType.LIMIT,
        StarbaseTimeInForce.GTC,
        false,
        false,
        false,
        1L,
        1L,
        null,
        true,
        null,
        null,
        BigDecimal.ZERO);
  }

  private static void await(Check check) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (!check.test() && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertTrue(check.test(), "condition was not reached before timeout");
  }

  @FunctionalInterface
  private interface Check {
    boolean test();
  }

  private static final class MutableClock implements NanoClock {
    private volatile long now;

    @Override
    public long nanoTime() {
      return now;
    }
  }

  private static final class ScriptedTransport implements OrderEntryDuplexTransport {
    private final ArrayDeque<byte[]> inbound = new ArrayDeque<>();
    private final ArrayList<byte[]> outbound = new ArrayList<>();
    private boolean opened;
    private boolean closed;
    private boolean failNextWrite;
    private boolean discardWrites;

    @Override
    public synchronized void open() {
      opened = true;
      notifyAll();
    }

    @Override
    public synchronized int read(ByteBuffer buffer) {
      while (inbound.isEmpty() && !closed) {
        try {
          wait();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return -1;
        }
      }
      if (closed) {
        return -1;
      }
      byte[] frame = inbound.removeFirst();
      for (int index = 0; index < frame.length; index++) {
        buffer.put(index, frame[index]);
      }
      return frame.length;
    }

    @Override
    public synchronized int write(ByteBuffer buffer, int offset, int length) {
      if (!opened || closed) {
        throw new IllegalStateException("scripted transport is not open");
      }
      if (failNextWrite) {
        failNextWrite = false;
        throw new IllegalStateException("ambiguous scripted write failure");
      }
      if (discardWrites) {
        return length;
      }
      byte[] frame = new byte[length];
      for (int index = 0; index < length; index++) {
        frame[index] = buffer.get(offset + index);
      }
      outbound.add(frame);
      notifyAll();
      return length;
    }

    synchronized void enqueue(ByteBuffer frame) {
      int length = TcpHeaderCodec.validateFrame(frame, 0);
      byte[] bytes = new byte[length];
      for (int index = 0; index < length; index++) {
        bytes[index] = frame.get(index);
      }
      inbound.addLast(bytes);
      notifyAll();
    }

    synchronized int outboundCount() {
      return outbound.size();
    }

    synchronized ByteBuffer outboundFrame(int index) {
      return ByteBuffer.wrap(outbound.get(index)).order(ByteOrder.LITTLE_ENDIAN);
    }

    synchronized int outboundTemplateCount(int templateId) {
      int count = 0;
      for (byte[] frame : outbound) {
        ByteBuffer wrapped = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        if (TcpHeaderCodec.messageTypeId(wrapped, 0) == templateId) {
          count++;
        }
      }
      return count;
    }

    synchronized boolean isClosed() {
      return closed;
    }

    synchronized void endInput() {
      closed = true;
      notifyAll();
    }

    synchronized void failNextWrite() {
      failNextWrite = true;
    }

    synchronized void discardWrites() {
      discardWrites = true;
    }

    @Override
    public synchronized void close() {
      closed = true;
      notifyAll();
    }
  }

  private static final class ScriptedTransportFactory
      implements Function<StarbaseOrderEntryContext, OrderEntryDuplexTransport> {
    private final ArrayDeque<ScriptedTransport> sideA = new ArrayDeque<>();
    private final ScriptedTransport sideB;

    private ScriptedTransportFactory(
        ScriptedTransport firstA, ScriptedTransport secondA, ScriptedTransport sideB) {
      sideA.add(firstA);
      sideA.add(secondA);
      this.sideB = sideB;
    }

    @Override
    public synchronized OrderEntryDuplexTransport apply(StarbaseOrderEntryContext context) {
      if (context.gatewaySide() == GatewaySide.B) {
        return sideB;
      }
      if (sideA.isEmpty()) {
        throw new IllegalStateException("no scripted A transport remains");
      }
      return sideA.removeFirst();
    }
  }
}
