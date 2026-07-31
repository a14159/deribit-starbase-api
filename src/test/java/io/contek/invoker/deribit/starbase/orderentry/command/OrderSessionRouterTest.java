package io.contek.invoker.deribit.starbase.orderentry.command;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import java.lang.management.ManagementFactory;

public final class OrderSessionRouterTest {

  private static volatile long sink;

  public void testReadyAIsSelectedOnceAndRecordedAsTheClientOrdersOrigin() {
    FakeEndpoint sideA = new FakeEndpoint(11, ProductGroup.BTC, GatewaySide.A, true, 1001);
    FakeEndpoint sideB = new FakeEndpoint(22, ProductGroup.BTC, GatewaySide.B, true, 2001);
    OrderSessionRouter router = new OrderSessionRouter(4, sideA, sideB);

    long correlationId = router.routeNewOrder(ProductGroup.BTC, 901);

    assertEquals(1001, correlationId);
    assertEquals(1, sideA.sends);
    assertEquals(0, sideB.sends);
    assertEquals(11, router.originSessionId(901));
  }

  public void testUnreadyASelectsBButNeverSendsToBoth() {
    FakeEndpoint sideA = new FakeEndpoint(11, ProductGroup.BTC, GatewaySide.A, false, 1001);
    FakeEndpoint sideB = new FakeEndpoint(22, ProductGroup.BTC, GatewaySide.B, true, 2001);
    OrderSessionRouter router = new OrderSessionRouter(2, sideA, sideB);
    assertEquals(2001, router.routeNewOrder(ProductGroup.BTC, 902));
    assertEquals(0, sideA.sends);
    assertEquals(1, sideB.sends);
    assertEquals(22, router.originSessionId(902));
  }

  public void testUnavailableProductFailsBeforeSendOrOriginMutation() {
    FakeEndpoint sideA = new FakeEndpoint(11, ProductGroup.BTC, GatewaySide.A, false, 1);
    FakeEndpoint sideB = new FakeEndpoint(22, ProductGroup.BTC, GatewaySide.B, false, 2);
    OrderSessionRouter router = new OrderSessionRouter(2, sideA, sideB);
    assertThrows(
        IllegalStateException.class,
        () -> router.routeNewOrder(ProductGroup.BTC, 1));
    assertThrows(
        IllegalStateException.class,
        () -> router.routeNewOrder(ProductGroup.ETH, 1));
    assertEquals(0, sideA.sends);
    assertEquals(0, sideB.sends);
    assertEquals(0, router.size());
  }

  public void testSelectedEndpointFailureNeverRetriesOtherSide() {
    FakeEndpoint sideA = new FakeEndpoint(11, ProductGroup.BTC, GatewaySide.A, true, 1);
    FakeEndpoint sideB = new FakeEndpoint(22, ProductGroup.BTC, GatewaySide.B, true, 2);
    sideA.fail = true;
    OrderSessionRouter router = new OrderSessionRouter(2, sideA, sideB);
    assertThrows(
        IllegalStateException.class,
        () -> router.routeNewOrder(ProductGroup.BTC, 1));
    assertEquals(1, sideA.sends);
    assertEquals(0, sideB.sends);
    assertEquals(0, router.size());
  }

  public void testDuplicateClientAndCapacityFailBeforeSendUntilOriginIsReleased() {
    FakeEndpoint sideA = new FakeEndpoint(11, ProductGroup.BTC, GatewaySide.A, true, 1);
    OrderSessionRouter router = new OrderSessionRouter(1, sideA);
    router.routeNewOrder(ProductGroup.BTC, 1);
    assertThrows(
        IllegalStateException.class,
        () -> router.routeNewOrder(ProductGroup.BTC, 1));
    assertThrows(
        IllegalStateException.class,
        () -> router.routeNewOrder(ProductGroup.BTC, 2));
    assertEquals(1, sideA.sends);
    assertFalse(router.releaseOrigin(999));
    assertTrue(router.releaseOrigin(1));
    router.routeNewOrder(ProductGroup.BTC, 2);
    assertEquals(2, sideA.sends);
    assertThrows(IllegalArgumentException.class, () -> router.originSessionId(1));
  }

  public void testEndpointConfigurationIsExplicitAndUniquePerProductAndSide() {
    FakeEndpoint first = new FakeEndpoint(11, ProductGroup.BTC, GatewaySide.A, true, 1);
    FakeEndpoint duplicate = new FakeEndpoint(12, ProductGroup.BTC, GatewaySide.A, true, 2);
    assertThrows(IllegalArgumentException.class, () -> new OrderSessionRouter(2, first, duplicate));
    assertThrows(IllegalArgumentException.class, () -> new OrderSessionRouter(0, first));
    assertThrows(NullPointerException.class, () -> new OrderSessionRouter(2, first, null));
  }

  public void testWarmedRouteOriginAndReleaseAllocateNothing() {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      return;
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    FakeEndpoint endpoint = new FakeEndpoint(11, ProductGroup.BTC, GatewaySide.A, true, 1);
    OrderSessionRouter router = new OrderSessionRouter(1, endpoint);
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      exercise(router, iteration);
    }
    long threadId = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      exercise(router, 1_000_000L + iteration);
    }
    assertEquals(
        0L,
        bean.getThreadAllocatedBytes(threadId) - before,
        "order session routing allocated bytes");
  }

  private static void exercise(OrderSessionRouter router, long clientOrderId) {
    long correlation = router.routeNewOrder(ProductGroup.BTC, clientOrderId);
    if (router.originSessionId(clientOrderId) != 11 || !router.releaseOrigin(clientOrderId)) {
      throw new AssertionError("route lifecycle failed");
    }
    sink = correlation;
  }

  private static final class FakeEndpoint implements OrderRouteEndpoint {
    private final long sessionId;
    private final ProductGroup productGroup;
    private final GatewaySide gatewaySide;
    private final boolean ready;
    private final long correlationId;
    private int sends;
    private boolean fail;

    private FakeEndpoint(
        long sessionId,
        ProductGroup productGroup,
        GatewaySide gatewaySide,
        boolean ready,
        long correlationId) {
      this.sessionId = sessionId;
      this.productGroup = productGroup;
      this.gatewaySide = gatewaySide;
      this.ready = ready;
      this.correlationId = correlationId;
    }

    @Override
    public long sessionId() {
      return sessionId;
    }

    @Override
    public ProductGroup productGroup() {
      return productGroup;
    }

    @Override
    public GatewaySide gatewaySide() {
      return gatewaySide;
    }

    @Override
    public boolean isReady() {
      return ready;
    }

    @Override
    public long submitNewOrder(long clientOrderId) {
      sends++;
      if (fail) {
        throw new IllegalStateException("ambiguous send failure");
      }
      return correlationId;
    }
  }
}
