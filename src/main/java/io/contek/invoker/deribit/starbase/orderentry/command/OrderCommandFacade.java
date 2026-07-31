package io.contek.invoker.deribit.starbase.orderentry.command;

import io.contek.invoker.deribit.starbase.codec.orderentry.AmendOrderRequestEncoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.CancelOrderRequestEncoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.MassCancelRequestEncoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.NewOrderRequestEncoder;
import io.contek.invoker.deribit.starbase.common.NanoClock;
import io.contek.invoker.deribit.starbase.orderentry.connection.SessionSequenceState;
import io.contek.invoker.deribit.starbase.orderentry.connection.TcpFrameEncoder;
import io.contek.invoker.deribit.starbase.orderentry.connection.TcpFrameWriter;
import io.contek.invoker.deribit.starbase.orderentry.state.CorrelationTable;
import java.nio.ByteBuffer;
import java.util.Objects;

/** Serialized allocation-conscious outbound order command encoder and correlator. */
public final class OrderCommandFacade implements TcpFrameEncoder {

  public static final int COMMAND_NEW = 1;
  public static final int COMMAND_AMEND = 2;
  public static final int COMMAND_CANCEL = 3;
  public static final int COMMAND_MASS_CANCEL = 4;

  private static final int ENCODE_NEW_LIMIT = 1;
  private static final int ENCODE_NEW_MARKET = 2;
  private static final int ENCODE_AMEND = 3;
  private static final int ENCODE_CANCEL = 4;
  private static final int ENCODE_MASS_CANCEL = 5;

  private final OrderCommandReadiness readiness;
  private final TcpFrameWriter writer;
  private final CorrelationTable correlations;
  private final SessionSequenceState sequences;
  private final NanoClock clock;
  private final long timeoutNanos;

  private long clientOrderId;
  private long correlationId;
  private long instrumentId;
  private long priceMantissa;
  private long quantityMantissa;
  private int quantityExponent;
  private boolean showQuantityNull;
  private long showQuantityMantissa;
  private long selfMatchPreventionId;
  private int side;
  private int timeInForce;
  private int flags;
  private int selfTradingMode;
  private long currencyPairId;
  private int productType;
  private int encodeKind;
  private long sequence;
  private long lastProcessedSequence;
  private long sendTimeNanos;

  public OrderCommandFacade(
      OrderCommandReadiness readiness,
      TcpFrameWriter writer,
      CorrelationTable correlations,
      SessionSequenceState sequences,
      NanoClock clock,
      long timeoutNanos) {
    this.readiness = Objects.requireNonNull(readiness, "readiness");
    this.writer = Objects.requireNonNull(writer, "writer");
    this.correlations = Objects.requireNonNull(correlations, "correlations");
    this.sequences = Objects.requireNonNull(sequences, "sequences");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (timeoutNanos < 1) {
      throw new IllegalArgumentException("timeoutNanos must be positive");
    }
    this.timeoutNanos = timeoutNanos;
  }

  public synchronized long newLimit(
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
    requireWritable();
    NewOrderRequestEncoder.validateLimit(
        clientOrderId, instrumentId, priceMantissa, quantityMantissa, quantityExponent,
        showQuantityNull, showQuantityMantissa, side, timeInForce, flags, selfTradingMode);
    this.clientOrderId = clientOrderId;
    this.instrumentId = instrumentId;
    this.priceMantissa = priceMantissa;
    this.quantityMantissa = quantityMantissa;
    this.quantityExponent = quantityExponent;
    this.showQuantityNull = showQuantityNull;
    this.showQuantityMantissa = showQuantityMantissa;
    this.selfMatchPreventionId = selfMatchPreventionId;
    this.side = side;
    this.timeInForce = timeInForce;
    this.flags = flags;
    this.selfTradingMode = selfTradingMode;
    encodeKind = ENCODE_NEW_LIMIT;
    return send(COMMAND_NEW, clientOrderId);
  }

  public synchronized long newMarket(
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
    requireWritable();
    NewOrderRequestEncoder.validateMarket(
        clientOrderId, instrumentId, quantityMantissa, quantityExponent, showQuantityNull,
        showQuantityMantissa, side, timeInForce, flags, selfTradingMode);
    this.clientOrderId = clientOrderId;
    this.instrumentId = instrumentId;
    this.quantityMantissa = quantityMantissa;
    this.quantityExponent = quantityExponent;
    this.showQuantityNull = showQuantityNull;
    this.showQuantityMantissa = showQuantityMantissa;
    this.selfMatchPreventionId = selfMatchPreventionId;
    this.side = side;
    this.timeInForce = timeInForce;
    this.flags = flags;
    this.selfTradingMode = selfTradingMode;
    encodeKind = ENCODE_NEW_MARKET;
    return send(COMMAND_NEW, clientOrderId);
  }

  public synchronized long amend(
      long clientOrderId,
      long instrumentId,
      long priceMantissa,
      long quantityMantissa,
      int quantityExponent,
      boolean showQuantityNull,
      long showQuantityMantissa,
      int flags) {
    requireWritable();
    AmendOrderRequestEncoder.validateArguments(
        clientOrderId, instrumentId, priceMantissa, quantityMantissa, quantityExponent,
        showQuantityNull, showQuantityMantissa, flags);
    this.clientOrderId = clientOrderId;
    this.instrumentId = instrumentId;
    this.priceMantissa = priceMantissa;
    this.quantityMantissa = quantityMantissa;
    this.quantityExponent = quantityExponent;
    this.showQuantityNull = showQuantityNull;
    this.showQuantityMantissa = showQuantityMantissa;
    this.flags = flags;
    encodeKind = ENCODE_AMEND;
    return send(COMMAND_AMEND, clientOrderId);
  }

  public synchronized long cancel(long clientOrderId, long instrumentId) {
    requireWritable();
    CancelOrderRequestEncoder.validateArguments(clientOrderId, instrumentId);
    this.clientOrderId = clientOrderId;
    this.instrumentId = instrumentId;
    encodeKind = ENCODE_CANCEL;
    return send(COMMAND_CANCEL, clientOrderId);
  }

  public synchronized long massCancel(
      long currencyPairId, long instrumentId, int productType, int side) {
    requireWritable();
    MassCancelRequestEncoder.validateScope(currencyPairId, instrumentId, productType, side);
    this.currencyPairId = currencyPairId;
    this.instrumentId = instrumentId;
    this.productType = productType;
    this.side = side;
    encodeKind = ENCODE_MASS_CANCEL;
    return send(COMMAND_MASS_CANCEL, 0);
  }

  @Override
  public int encode(ByteBuffer buffer, int offset) {
    return switch (encodeKind) {
      case ENCODE_NEW_LIMIT ->
          NewOrderRequestEncoder.encodeLimit(
              buffer, offset, clientOrderId, correlationId, instrumentId, priceMantissa,
              quantityMantissa, quantityExponent, showQuantityNull, showQuantityMantissa,
              selfMatchPreventionId, side, timeInForce, flags, selfTradingMode, sequence,
              lastProcessedSequence, sendTimeNanos);
      case ENCODE_NEW_MARKET ->
          NewOrderRequestEncoder.encodeMarket(
              buffer, offset, clientOrderId, correlationId, instrumentId, quantityMantissa,
              quantityExponent, showQuantityNull, showQuantityMantissa, selfMatchPreventionId,
              side, timeInForce, flags, selfTradingMode, sequence, lastProcessedSequence,
              sendTimeNanos);
      case ENCODE_AMEND ->
          AmendOrderRequestEncoder.encode(
              buffer, offset, clientOrderId, correlationId, instrumentId, priceMantissa,
              quantityMantissa, quantityExponent, showQuantityNull, showQuantityMantissa, flags,
              sequence, lastProcessedSequence, sendTimeNanos);
      case ENCODE_CANCEL ->
          CancelOrderRequestEncoder.encode(
              buffer, offset, clientOrderId, correlationId, instrumentId, sequence,
              lastProcessedSequence, sendTimeNanos);
      case ENCODE_MASS_CANCEL ->
          MassCancelRequestEncoder.encode(
              buffer, offset, correlationId, currencyPairId, instrumentId, productType, side,
              sequence, lastProcessedSequence, sendTimeNanos);
      default -> throw new IllegalStateException("no order command selected");
    };
  }

  private long send(int commandType, long correlationClientOrderId) {
    long now = clock.nanoTime();
    long registered =
        correlations.register(commandType, correlationClientOrderId, now, timeoutNanos);
    correlationId = registered;
    sequence = sequences.claimOutboundSequence();
    lastProcessedSequence = sequences.lastProcessedInbound();
    sendTimeNanos = now;
    try {
      writer.write(this);
    } catch (RuntimeException failure) {
      correlations.release(registered);
      throw failure;
    }
    return registered;
  }

  private void requireWritable() {
    if (!readiness.isReady()) {
      throw new IllegalStateException("order entry is not ready");
    }
    if (writer.pendingBytes() != 0) {
      throw new IllegalStateException("previous order frame remains pending");
    }
  }
}
