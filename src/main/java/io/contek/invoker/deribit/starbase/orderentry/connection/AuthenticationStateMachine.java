package io.contek.invoker.deribit.starbase.orderentry.connection;

import io.contek.invoker.deribit.starbase.codec.orderentry.LogonConfirmationCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.LogonEncoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.OrderEntryMessageHandler;
import io.contek.invoker.deribit.starbase.codec.orderentry.SessionRejectDecoder;
import io.contek.invoker.deribit.starbase.common.NanoClock;
import io.contek.invoker.deribit.starbase.common.StarbaseCredentials;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

/** Fail-closed authentication lifecycle for one freshly opened order-entry session. */
public final class AuthenticationStateMachine
    implements OrderEntryMessageHandler, TcpFrameEncoder, AutoCloseable {

  private static final int NEW = 0;
  private static final int WRITING_LOGON = 1;
  private static final int WAITING_CONFIRMATION = 2;
  private static final int AUTHENTICATED = 3;
  private static final int FAILED = 4;
  private static final int CLOSED = 5;

  public static final int FAILURE_NONE = 0;
  public static final int FAILURE_REJECTED = 1;
  public static final int FAILURE_TIMEOUT = 2;
  public static final int FAILURE_PROTOCOL = 3;

  private final TcpFrameWriter writer;
  private final NanoClock clock;
  private final long timeoutNanos;
  private char[] clientId;
  private char[] secret;
  private int state = NEW;
  private int failureCode;
  private int rejectReason;
  private int heartbeatIntervalSeconds;
  private long sequence;
  private long lastProcessedSequence;
  private long sentAtNanos;
  private boolean resetSequenceNumber;
  private boolean encoderInvoked;

  public AuthenticationStateMachine(
      TcpFrameWriter writer,
      NanoClock clock,
      Duration timeout,
      StarbaseCredentials credentials) {
    this.writer = Objects.requireNonNull(writer, "writer");
    this.clock = Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(timeout, "timeout");
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    try {
      this.timeoutNanos = timeout.toNanos();
    } catch (ArithmeticException overflow) {
      throw new IllegalArgumentException("timeout is too large", overflow);
    }
    Objects.requireNonNull(credentials, "credentials");
    this.clientId = credentials.copyUsername();
    this.secret = credentials.copyPassword();
  }

  public synchronized boolean begin(
      long sequence, long lastProcessedSequence, boolean resetSequenceNumber) {
    if (state != NEW) {
      throw new IllegalStateException("authentication already started");
    }
    if (sequence < 1 || lastProcessedSequence < 0) {
      throw new IllegalArgumentException("invalid logon sequence state");
    }
    this.sequence = sequence;
    this.lastProcessedSequence = lastProcessedSequence;
    this.resetSequenceNumber = resetSequenceNumber;
    state = WRITING_LOGON;
    encoderInvoked = false;
    boolean complete;
    try {
      complete = writer.write(this);
    } catch (RuntimeException failure) {
      fail(FAILURE_PROTOCOL);
      throw failure;
    } finally {
      if (encoderInvoked) {
        destroyCredentialCopies();
      }
    }
    if (complete) {
      markSent();
    }
    return complete;
  }

  public synchronized boolean flushLogon() {
    if (state != WRITING_LOGON) {
      throw new IllegalStateException("no partial logon is pending");
    }
    boolean complete = writer.flush();
    if (complete) {
      markSent();
    }
    return complete;
  }

  @Override
  public synchronized int encode(ByteBuffer buffer, int offset) {
    if (state != WRITING_LOGON || clientId == null || secret == null) {
      throw new IllegalStateException("logon encoder is not available");
    }
    encoderInvoked = true;
    return LogonEncoder.encode(
        buffer,
        offset,
        clientId,
        secret,
        resetSequenceNumber,
        sequence,
        lastProcessedSequence,
        clock.nanoTime());
  }

  @Override
  public synchronized void onMessage(
      int templateId, ByteBuffer buffer, int offset) {
    if (state != WAITING_CONFIRMATION) {
      fail(FAILURE_PROTOCOL);
      throw new StarbaseProtocolException("unexpected authentication response");
    }
    if (templateId == LogonConfirmationCodec.TEMPLATE_ID) {
      LogonConfirmationCodec.validate(buffer, offset);
      heartbeatIntervalSeconds =
          LogonConfirmationCodec.heartbeatIntervalSeconds(buffer, offset);
      state = AUTHENTICATED;
      return;
    }
    if (templateId == SessionRejectDecoder.TEMPLATE_ID) {
      SessionRejectDecoder.validate(buffer, offset);
      if (SessionRejectDecoder.refSequenceNumber(buffer, offset) != sequence) {
        fail(FAILURE_PROTOCOL);
        throw new StarbaseProtocolException("session reject does not reference logon");
      }
      rejectReason = SessionRejectDecoder.reason(buffer, offset);
      fail(FAILURE_REJECTED);
      return;
    }
    fail(FAILURE_PROTOCOL);
    throw new StarbaseProtocolException(
        "unexpected pre-authentication template: " + templateId);
  }

  public synchronized boolean checkTimeout() {
    if (state != WAITING_CONFIRMATION) {
      return state == FAILED;
    }
    long elapsed = clock.nanoTime() - sentAtNanos;
    if (elapsed >= timeoutNanos && elapsed >= 0) {
      fail(FAILURE_TIMEOUT);
      return true;
    }
    return false;
  }

  public synchronized boolean isAuthenticated() {
    return state == AUTHENTICATED;
  }

  public synchronized boolean isFailed() {
    return state == FAILED;
  }

  public synchronized int failureCode() {
    return failureCode;
  }

  public synchronized int rejectReason() {
    return rejectReason;
  }

  public synchronized int heartbeatIntervalSeconds() {
    return heartbeatIntervalSeconds;
  }

  synchronized boolean credentialsDestroyed() {
    return clientId == null;
  }

  @Override
  public synchronized void close() {
    destroyCredentialCopies();
    if (state != FAILED) {
      state = CLOSED;
    }
  }

  private void markSent() {
    state = WAITING_CONFIRMATION;
    sentAtNanos = clock.nanoTime();
  }

  private void fail(int code) {
    failureCode = code;
    state = FAILED;
    destroyCredentialCopies();
  }

  private void destroyCredentialCopies() {
    if (clientId != null) {
      Arrays.fill(clientId, '\0');
      Arrays.fill(secret, '\0');
      clientId = null;
      secret = null;
    }
  }
}
