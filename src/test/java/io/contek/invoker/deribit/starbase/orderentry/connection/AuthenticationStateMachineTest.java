package io.contek.invoker.deribit.starbase.orderentry.connection;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.codec.orderentry.LogonConfirmationCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.LogonDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.SessionRejectDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.SessionRejectEncoder;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import io.contek.invoker.deribit.starbase.common.StarbaseCredentials;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;

public final class AuthenticationStateMachineTest {

  public void testValidLogonConfirmationAuthenticatesOnlyAfterTheRequestWasSent() {
    MutableClock clock = new MutableClock();
    CapturingTransport transport = new CapturingTransport();
    TcpFrameWriter writer = new TcpFrameWriter(256, transport);
    try (StarbaseCredentials credentials =
        new StarbaseCredentials(new char[] {'c'}, new char[] {'s'})) {
      AuthenticationStateMachine authentication =
          new AuthenticationStateMachine(writer, clock, Duration.ofSeconds(5), credentials);

      assertFalse(authentication.isAuthenticated());
      assertTrue(authentication.begin(1, 0, false));
      assertEquals(1, transport.writes);
      LogonDecoder.validate(transport.frame, 0);
      assertTrue(authentication.credentialsDestroyed());

      ByteBuffer confirmation = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
      LogonConfirmationCodec.encode(confirmation, 0, 10, 1, 1, 2);
      authentication.onMessage(
          LogonConfirmationCodec.TEMPLATE_ID, confirmation, 0);

      assertTrue(authentication.isAuthenticated());
      assertEquals(10, authentication.heartbeatIntervalSeconds());
    }
  }

  public void testPartialLogonStartsTimeoutOnlyAfterFlushAndCredentialsAreImmediatelyErased() {
    MutableClock clock = new MutableClock();
    PartialTransport transport = new PartialTransport();
    TcpFrameWriter writer = new TcpFrameWriter(256, transport);
    StarbaseCredentials credentials =
        new StarbaseCredentials(new char[] {'c'}, new char[] {'s'});
    AuthenticationStateMachine authentication =
        new AuthenticationStateMachine(writer, clock, Duration.ofNanos(10), credentials);
    credentials.close();

    assertFalse(authentication.begin(1, 0, false));
    assertTrue(authentication.credentialsDestroyed());
    clock.now = 100;
    assertFalse(authentication.checkTimeout());

    transport.writable = true;
    assertTrue(authentication.flushLogon());
    clock.now = 109;
    assertFalse(authentication.checkTimeout());
    clock.now = 110;
    assertTrue(authentication.checkTimeout());
    assertTrue(authentication.isFailed());
    assertEquals(AuthenticationStateMachine.FAILURE_TIMEOUT, authentication.failureCode());
  }

  public void testReferencedSessionRejectFailsAuthenticationAndRetainsPrimitiveReason() {
    MutableClock clock = new MutableClock();
    CapturingTransport transport = new CapturingTransport();
    AuthenticationStateMachine authentication = machine(clock, transport);
    assertTrue(authentication.begin(7, 0, false));
    ByteBuffer reject = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    SessionRejectEncoder.encode(reject, 0, 7, 3, new char[] {'n', 'o'}, 1, 7, 2);

    authentication.onMessage(SessionRejectDecoder.TEMPLATE_ID, reject, 0);

    assertTrue(authentication.isFailed());
    assertEquals(AuthenticationStateMachine.FAILURE_REJECTED, authentication.failureCode());
    assertEquals(3, authentication.rejectReason());
  }

  public void testPreAuthAndDuplicateResponsesFailClosed() {
    MutableClock clock = new MutableClock();
    ByteBuffer confirmation = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
    LogonConfirmationCodec.encode(confirmation, 0, 10, 1, 1, 2);
    AuthenticationStateMachine beforeBegin = machine(clock, new CapturingTransport());
    assertThrows(
        StarbaseProtocolException.class,
        () ->
            beforeBegin.onMessage(
                LogonConfirmationCodec.TEMPLATE_ID, confirmation, 0));
    assertTrue(beforeBegin.isFailed());

    AuthenticationStateMachine duplicate = machine(clock, new CapturingTransport());
    assertTrue(duplicate.begin(1, 0, false));
    duplicate.onMessage(LogonConfirmationCodec.TEMPLATE_ID, confirmation, 0);
    assertThrows(
        StarbaseProtocolException.class,
        () -> duplicate.onMessage(LogonConfirmationCodec.TEMPLATE_ID, confirmation, 0));
    assertTrue(duplicate.isFailed());
    assertEquals(AuthenticationStateMachine.FAILURE_PROTOCOL, duplicate.failureCode());
  }

  public void testWrongRejectReferenceAndUnexpectedTemplateFailClosed() {
    MutableClock clock = new MutableClock();
    AuthenticationStateMachine wrongReference = machine(clock, new CapturingTransport());
    wrongReference.begin(7, 0, false);
    ByteBuffer reject = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    SessionRejectEncoder.encode(reject, 0, 8, 2, new char[0], 1, 7, 2);
    assertThrows(
        StarbaseProtocolException.class,
        () -> wrongReference.onMessage(SessionRejectDecoder.TEMPLATE_ID, reject, 0));
    assertTrue(wrongReference.isFailed());

    AuthenticationStateMachine unexpected = machine(clock, new CapturingTransport());
    unexpected.begin(1, 0, false);
    assertThrows(
        StarbaseProtocolException.class,
        () -> unexpected.onMessage(10, ByteBuffer.allocate(40), 0));
    assertTrue(unexpected.isFailed());
  }

  private static AuthenticationStateMachine machine(
      MutableClock clock, TcpFrameTransport transport) {
    return new AuthenticationStateMachine(
        new TcpFrameWriter(256, transport),
        clock,
        Duration.ofSeconds(5),
        new StarbaseCredentials(new char[] {'c'}, new char[] {'s'}));
  }

  private static final class MutableClock
      implements io.contek.invoker.deribit.starbase.common.NanoClock {
    private long now;

    @Override
    public long nanoTime() {
      return now;
    }
  }

  private static final class CapturingTransport implements TcpFrameTransport {
    private final ByteBuffer frame = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
    private int writes;

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      for (int index = 0; index < length; index++) {
        frame.put(index, buffer.get(offset + index));
      }
      writes++;
      return length;
    }
  }

  private static final class PartialTransport implements TcpFrameTransport {
    private boolean first = true;
    private boolean writable;

    @Override
    public int write(ByteBuffer buffer, int offset, int length) {
      if (first) {
        first = false;
        return 20;
      }
      return writable ? length : 0;
    }
  }
}
