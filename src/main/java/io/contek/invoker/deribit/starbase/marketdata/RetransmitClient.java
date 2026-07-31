package io.contek.invoker.deribit.starbase.marketdata;

import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.UdpPacketHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.marketdata.MarketDataMessageHandler;
import io.contek.invoker.deribit.starbase.codec.marketdata.MarketDataPacketDispatcher;
import io.contek.invoker.deribit.starbase.codec.marketdata.RetransmitRejectDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.RetransmitRequestEncoder;
import io.contek.invoker.deribit.starbase.common.NanoClock;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Objects;

/** Synchronous, reusable-buffer retransmit recovery state machine. */
public final class RetransmitClient {

  public static final int COMPLETE = 1;
  public static final int UNRECOVERABLE = 2;

  public static final int FAILURE_NONE = 0;
  public static final int FAILURE_TIMEOUT = -1;

  private final RetransmitTransport transport;
  private final NanoClock clock;
  private final long timeoutNanos;
  private final int maxRetries;
  private final ByteBuffer requestBuffer;
  private final ByteBuffer responseBuffer;
  private final MarketDataPacketDispatcher dispatcher;

  private int requestCount;
  private int retryCount;
  private int failureReason;
  private long retryDelayNanos;
  private long nextSequence;

  public RetransmitClient(
      RetransmitTransport transport,
      NanoClock clock,
      Duration timeout,
      int maxRetries,
      int responseBufferBytes,
      MarketDataMessageHandler handler) {
    this.transport = Objects.requireNonNull(transport, "transport");
    this.clock = Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(timeout, "timeout");
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    try {
      timeoutNanos = timeout.toNanos();
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("timeout is too large", exception);
    }
    if (maxRetries < 0) {
      throw new IllegalArgumentException("maxRetries must be non-negative");
    }
    if (responseBufferBytes < UdpPacketHeaderCodec.ENCODED_LENGTH) {
      throw new IllegalArgumentException("responseBufferBytes must be at least 24");
    }
    this.maxRetries = maxRetries;
    requestBuffer =
        ByteBuffer.allocateDirect(RetransmitRequestEncoder.ENCODED_LENGTH)
            .order(ByteOrder.LITTLE_ENDIAN);
    responseBuffer =
        ByteBuffer.allocateDirect(responseBufferBytes).order(ByteOrder.LITTLE_ENDIAN);
    MarketDataMessageHandler delegate = Objects.requireNonNull(handler, "handler");
    dispatcher =
        new MarketDataPacketDispatcher(
            (buffer, offset, templateId, sequence) -> {
              if (templateId != RetransmitRejectDecoder.TEMPLATE_ID) {
                delegate.onMessage(buffer, offset, templateId, sequence);
              }
            });
  }

  public int recover(long beginSequence, long missingMessageCount) throws IOException {
    validateRange(beginSequence, missingMessageCount);
    requestCount = 0;
    retryCount = 0;
    failureReason = FAILURE_NONE;
    retryDelayNanos = 0;
    nextSequence = beginSequence;
    long remaining = missingMessageCount;

    while (remaining > 0) {
      int requested =
          (int) Math.min(remaining, RetransmitRequestEncoder.MAX_MESSAGE_COUNT);
      int pageRetries = 0;
      while (true) {
        encodeAndSend(nextSequence, requested);
        responseBuffer.clear();
        int length = transport.receive(responseBuffer, timeoutNanos);
        if (length == 0) {
          if (pageRetries == maxRetries) {
            failureReason = FAILURE_TIMEOUT;
            return UNRECOVERABLE;
          }
          pageRetries++;
          retryCount++;
          continue;
        }
        requireResponseLength(length);
        responseBuffer.limit(length);
        int packetType = UdpPacketHeaderCodec.type(responseBuffer, 0);
        if (packetType == UdpPacketHeaderCodec.TYPE_CONTROL) {
          return handleReject();
        }
        if (packetType != UdpPacketHeaderCodec.TYPE_RETRANSMIT_SUCCESS) {
          throw new StarbaseProtocolException(
              "unexpected retransmit response packet type: " + packetType);
        }
        long responseSequence = UdpPacketHeaderCodec.sequenceNumber(responseBuffer, 0);
        int returned = UdpPacketHeaderCodec.messageCount(responseBuffer, 0);
        if (responseSequence != nextSequence) {
          throw new StarbaseProtocolException(
              "retransmit response sequence mismatch: expected="
                  + nextSequence
                  + ", actual="
                  + responseSequence);
        }
        if (returned < 1 || returned > requested || returned > remaining) {
          throw new StarbaseProtocolException(
              "invalid retransmit returned message count: " + returned);
        }
        int dispatched = dispatcher.dispatch(responseBuffer, 0);
        if (dispatched != returned) {
          throw new StarbaseProtocolException("retransmit dispatched count mismatch");
        }
        nextSequence += returned;
        remaining -= returned;
        break;
      }
    }
    return COMPLETE;
  }

  public int requestCount() {
    return requestCount;
  }

  public int retryCount() {
    return retryCount;
  }

  public int failureReason() {
    return failureReason;
  }

  public long retryDelayNanos() {
    return retryDelayNanos;
  }

  public long nextSequence() {
    return nextSequence;
  }

  private void encodeAndSend(long sequence, int count) throws IOException {
    requestBuffer.clear();
    RetransmitRequestEncoder.encode(requestBuffer, 0, sequence, count, clock.nanoTime());
    requestBuffer.limit(RetransmitRequestEncoder.ENCODED_LENGTH);
    transport.send(requestBuffer, RetransmitRequestEncoder.ENCODED_LENGTH);
    requestCount++;
  }

  private int handleReject() {
    int messageCount = UdpPacketHeaderCodec.messageCount(responseBuffer, 0);
    int messageOffset = UdpPacketHeaderCodec.ENCODED_LENGTH;
    if (messageCount != 1) {
      throw new StarbaseProtocolException(
          "RetransmitReject packet must contain exactly one message");
    }
    MarketDataMessageHeaderCodec.validate(responseBuffer, messageOffset);
    if (MarketDataMessageHeaderCodec.templateId(responseBuffer, messageOffset)
        != RetransmitRejectDecoder.TEMPLATE_ID) {
      throw new StarbaseProtocolException("control response is not RetransmitReject");
    }
    dispatcher.dispatch(responseBuffer, 0);
    failureReason = RetransmitRejectDecoder.reason(responseBuffer, messageOffset);
    retryDelayNanos =
        RetransmitRejectDecoder.retryDelayNanos(responseBuffer, messageOffset);
    return UNRECOVERABLE;
  }

  private void requireResponseLength(int length) {
    if (length < UdpPacketHeaderCodec.ENCODED_LENGTH
        || length > responseBuffer.capacity()) {
      throw new StarbaseProtocolException(
          "invalid retransmit datagram length: " + length);
    }
  }

  private static void validateRange(long beginSequence, long missingMessageCount) {
    if (beginSequence < 0) {
      throw new IllegalArgumentException("beginSequence must be non-negative");
    }
    if (missingMessageCount < 1) {
      throw new IllegalArgumentException("missingMessageCount must be positive");
    }
    try {
      Math.addExact(beginSequence, missingMessageCount);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("retransmit sequence range overflows", exception);
    }
  }
}
