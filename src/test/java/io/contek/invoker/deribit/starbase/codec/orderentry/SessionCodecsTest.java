package io.contek.invoker.deribit.starbase.codec.orderentry;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;

import com.sun.management.ThreadMXBean;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class SessionCodecsTest {

  private static volatile long sink;

  public void testLogonEncoderAndDecoderPinCredentialsResetAndPadding() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    char[] client = "client-1".toCharArray();
    char[] secret = "secret-value".toCharArray();

    int encoded =
        LogonEncoder.encode(frame, 3, client, secret, true, 7L, 6L, 99L);

    assertEquals(104, encoded);
    assertEquals(1, TcpHeaderCodec.messageTypeId(frame, 3));
    assertEquals(97, TcpHeaderCodec.messageLength(frame, 3));
    LogonDecoder.validate(frame, 3);
    assertEquals('c', LogonDecoder.clientIdByte(frame, 3, 0));
    assertEquals(0, LogonDecoder.clientIdByte(frame, 3, 8));
    assertEquals('s', LogonDecoder.secretByte(frame, 3, 0));
    assertEquals(1, LogonDecoder.resetSequenceNumber(frame, 3));
    assertEquals(104, TcpHeaderCodec.validateFrame(frame, 3));
  }

  public void testHeartbeatAndRecoveryMessagesRetainExactSequences() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    HeartbeatCodec.encode(frame, 0, 123L, 10L, 9L, 100L);
    HeartbeatCodec.validate(frame, 0);
    assertEquals(123L, HeartbeatCodec.correlationId(frame, 0));

    ResendRequestCodec.encode(frame, 0, 50L, 75L, 11L, 10L, 101L);
    ResendRequestCodec.validate(frame, 0);
    assertEquals(50L, ResendRequestCodec.fromSequenceNumber(frame, 0));
    assertEquals(75L, ResendRequestCodec.toSequenceNumber(frame, 0));

    GapFillDecoder.validate(frameFor(21, 40, 88L), 0);
    assertEquals(88L, GapFillDecoder.newSequenceNumber(frameFor(21, 40, 88L), 0));
  }

  public void testCorruptCredentialsRangesAndRejectReasonsFailClosed() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LogonEncoder.encode(
                frame,
                0,
                "0123456789abcdefg".toCharArray(),
                "secret".toCharArray(),
                false,
                1L,
                0L,
                1L));
    ByteBuffer reject = frameFor(30, 42, 1L);
    reject.put(40, (byte) 0);
    reject.put(41, (byte) 0);
    assertThrows(StarbaseProtocolException.class, () -> SessionRejectDecoder.validate(reject, 0));
  }

  public void testConfirmationLogoutLoggedOutTestRequestGapFillAndRejectRoundTrip() {
    ByteBuffer frame = ByteBuffer.allocateDirect(512).order(ByteOrder.LITTLE_ENDIAN);

    assertEquals(
        40,
        LogonConfirmationCodec.encode(frame, 0, 30, 1L, 0L, 10L));
    LogonConfirmationCodec.validate(frame, 0);
    assertEquals(30, LogonConfirmationCodec.heartbeatIntervalSeconds(frame, 0));

    assertEquals(40, LogoutCodec.encode(frame, 0, "bye".toCharArray(), 2L, 1L, 11L));
    LogoutCodec.validate(frame, 0);
    assertEquals(3, LogoutCodec.reasonLength(frame, 0));
    assertEquals('e', LogoutCodec.reasonByte(frame, 0, 2));

    assertEquals(
        40, LoggedOutCodec.encode(frame, 0, new char[0], 3L, 2L, 12L));
    LoggedOutCodec.validate(frame, 0);
    assertEquals(0, LoggedOutCodec.reasonLength(frame, 0));

    TestRequestCodec.encode(frame, 0, 44L, 4L, 3L, 13L);
    TestRequestCodec.validate(frame, 0);
    assertEquals(44L, TestRequestCodec.correlationId(frame, 0));

    GapFillEncoder.encode(frame, 0, 90L, 5L, 4L, 14L);
    GapFillDecoder.validate(frame, 0);
    assertEquals(90L, GapFillDecoder.newSequenceNumber(frame, 0));

    assertEquals(
        48,
        SessionRejectEncoder.encode(
            frame, 0, 5L, 5, "bad".toCharArray(), 6L, 5L, 15L));
    SessionRejectDecoder.validate(frame, 0);
    assertEquals(5L, SessionRejectDecoder.refSequenceNumber(frame, 0));
    assertEquals(5, SessionRejectDecoder.reason(frame, 0));
    assertEquals(3, SessionRejectDecoder.detailsLength(frame, 0));
    assertEquals('b', SessionRejectDecoder.detailsByte(frame, 0, 0));
  }

  public void testWrongTemplateLengthPaddingAsciiBooleanAndTruncationFailClosed() {
    ByteBuffer frame = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    LogonEncoder.encode(
        frame,
        0,
        "client".toCharArray(),
        "secret".toCharArray(),
        false,
        1L,
        0L,
        1L);

    frame.put(32 + 7, (byte) 'x');
    assertThrows(StarbaseProtocolException.class, () -> LogonDecoder.validate(frame, 0));
    frame.put(32 + 7, (byte) 0);
    frame.put(32 + 64, (byte) 2);
    assertThrows(StarbaseProtocolException.class, () -> LogonDecoder.validate(frame, 0));
    frame.put(32 + 64, (byte) 0);
    frame.putShort(4, (short) 2);
    assertThrows(StarbaseProtocolException.class, () -> LogonDecoder.validate(frame, 0));
    frame.putShort(4, (short) 1);
    frame.put(100, (byte) 1);
    assertThrows(StarbaseProtocolException.class, () -> LogonDecoder.validate(frame, 0));
    frame.put(100, (byte) 0);
    frame.limit(103);
    assertThrows(StarbaseProtocolException.class, () -> LogonDecoder.validate(frame, 0));
  }

  public void testValidSessionDecodeAllocatesNothingAfterWarmup() {
    ByteBuffer logon = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    LogonEncoder.encode(
        logon,
        0,
        "client".toCharArray(),
        "secret".toCharArray(),
        false,
        1L,
        0L,
        1L);
    ByteBuffer heartbeat = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
    HeartbeatCodec.encode(heartbeat, 0, 1L, 2L, 1L, 2L);
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long threadId = Thread.currentThread().threadId();
    for (int iteration = 0; iteration < 1_000_000; iteration++) {
      LogonDecoder.validate(logon, 0);
      HeartbeatCodec.validate(heartbeat, 0);
      sink += LogonDecoder.clientIdByte(logon, 0, 0);
      sink += HeartbeatCodec.correlationId(heartbeat, 0);
    }
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int iteration = 0; iteration < 100_000; iteration++) {
      LogonDecoder.validate(logon, 0);
      HeartbeatCodec.validate(heartbeat, 0);
      sink += LogonDecoder.clientIdByte(logon, 0, 0);
      sink += HeartbeatCodec.correlationId(heartbeat, 0);
    }
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    assertEquals(0L, allocated);
  }

  private static ByteBuffer frameFor(int templateId, int length, long firstBodyLong) {
    ByteBuffer frame = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
    TcpHeaderCodec.encode(frame, 0, 0, length, templateId, 11, 1L, 0L, 1L);
    frame.putLong(32, firstBodyLong);
    TcpHeaderCodec.zeroPadding(frame, 0, length);
    return frame;
  }
}
