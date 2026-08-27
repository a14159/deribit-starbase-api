package io.contek.invoker.deribit.starbase.marketdata;

import io.contek.invoker.deribit.starbase.channel.StarbaseLongChannel;
import io.contek.invoker.deribit.starbase.channel.PrimitiveLongChannelRouter;
import io.contek.invoker.deribit.starbase.channel.StarbaseTradeChannel;
import io.contek.invoker.deribit.starbase.book.InstrumentRegistry;
import io.contek.invoker.deribit.starbase.codec.marketdata.MarketDataPacketDispatcher;
import io.contek.invoker.deribit.starbase.codec.marketdata.InstrumentDefinitionDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.IndexDefinitionDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.IndexInfoDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.InstrumentInfoDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.InstrumentRefDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.InstrumentStatusUpdateDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidPutDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.AskPutDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidQtyReducedDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.AskQtyReducedDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.BidDeleteDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.AskDeleteDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.EndOfCycleDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.TradeSummaryDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.TradeDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.SnapshotHeaderDecoder;
import io.contek.invoker.deribit.starbase.codec.marketdata.SnapshotTrailerDecoder;
import io.contek.invoker.deribit.starbase.codec.common.UdpPacketHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.common.AbstractStarbaseApi;
import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.StarbaseException;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redundant UDP receiver, recovery, reconstruction, and stable-channel API.
 *
 * <p>The two-context constructor composes the required A/B lifecycle. The single-context
 * constructor remains available for deterministic per-side diagnostics and compatibility,
 * but it can never report redundant synchronization readiness.
 */
public final class StarbaseMarketDataApi extends AbstractStarbaseApi {

  private static final int DEFAULT_INSTRUMENT_CAPACITY = 4096;
  private static final int DEFAULT_BOOK_ORDER_CAPACITY = 65_536;
  private static final int DEFAULT_BOOK_LEVEL_CAPACITY = 65_536;
  private static final int DEFAULT_RETRANSMIT_RETRIES = 2;

  private static final int SNAPSHOT_WAITING_FOR_BOUNDARY = 0;
  private static final int SNAPSHOT_COLLECTING = 1;
  private static final int SNAPSHOT_LIVE = 2;

  private final StarbaseMarketDataContext context;
  private final StarbaseMarketDataContext peerContext;
  private final PrimitiveLongChannelRouter orderBooks;
  private final TradeChannelRouter trades;
  private final TradeSummaryContext tradeSummary = new TradeSummaryContext();
  private final InstrumentRegistry instruments;
  private final OrderBookStateRouter reconstructedBooks;
  private final StarbaseLongChannel referenceData = new StarbaseLongChannel();
  private final MarketDataPacketDispatcher dispatcher;
  private final MarketDataPacketDispatcher incrementalDispatcherA;
  private final MarketDataPacketDispatcher incrementalDispatcherB;
  private final MarketDataPacketDispatcher snapshotDispatcherA;
  private final MarketDataPacketDispatcher snapshotDispatcherB;
  private final AtomicLong receivedPacketCount = new AtomicLong();
  private final FeedSequenceTracker incrementalSequence = new FeedSequenceTracker();
  private final FeedSequenceTracker snapshotSequence = new FeedSequenceTracker();
  private final FeedSequenceTracker peerIncrementalSequence = new FeedSequenceTracker();
  private final FeedSequenceTracker peerSnapshotSequence = new FeedSequenceTracker();
  private final FeedDiagnostics incrementalDiagnostics = new FeedDiagnostics();
  private final FeedDiagnostics snapshotDiagnostics = new FeedDiagnostics();
  private final FeedDiagnostics peerIncrementalDiagnostics = new FeedDiagnostics();
  private final FeedDiagnostics peerSnapshotDiagnostics = new FeedDiagnostics();
  private final FeedArbitrator incrementalArbitrator = new FeedArbitrator();
  private final FeedArbitrator snapshotArbitrator = new FeedArbitrator();

  private RetransmitTransport retransmitTransportA;
  private RetransmitTransport retransmitTransportB;
  private RetransmitClient retransmitClientA;
  private RetransmitClient retransmitClientB;
  private final int retransmitRetries;
  private final boolean injectedRetransmitTransports;
  private volatile boolean freshSnapshotRequired = true;
  private volatile boolean assemblyFailure;
  private int snapshotRecoveryState = SNAPSHOT_WAITING_FOR_BOUNDARY;
  private long snapshotGeneration;
  private boolean assemblySnapshotOpen;
  private boolean assemblySnapshotStateOpen;
  private long assemblySnapshotInstrumentId;
  private long assemblySnapshotAnchor;

  private volatile boolean transportRunning;
  private volatile MarketDataUdpReceiver incrementalReceiver;
  private volatile MarketDataUdpReceiver snapshotReceiver;
  private volatile MarketDataUdpReceiver peerIncrementalReceiver;
  private volatile MarketDataUdpReceiver peerSnapshotReceiver;
  private volatile Thread incrementalThread;
  private volatile Thread snapshotThread;
  private volatile Thread peerIncrementalThread;
  private volatile Thread peerSnapshotThread;
  private volatile Throwable transportFailure;
  private boolean replaySnapshotOpen;
  private long replaySnapshotInstrumentId;
  private int replaySnapshotFlags;
  private long replaySnapshotSequence;
  private long replaySnapshotTimestamp;

  public StarbaseMarketDataApi(StarbaseMarketDataContext context) {
    this(context, DEFAULT_INSTRUMENT_CAPACITY);
  }

  public StarbaseMarketDataApi(
      StarbaseMarketDataContext context, int instrumentCapacity) {
    this(
        Objects.requireNonNull(context, "context"),
        null,
        instrumentCapacity,
        null,
        null,
        DEFAULT_RETRANSMIT_RETRIES);
  }

  public StarbaseMarketDataApi(
      StarbaseMarketDataContext first, StarbaseMarketDataContext second) {
    this(
        first,
        second,
        DEFAULT_INSTRUMENT_CAPACITY,
        null,
        null,
        DEFAULT_RETRANSMIT_RETRIES);
  }

  StarbaseMarketDataApi(
      StarbaseMarketDataContext first,
      StarbaseMarketDataContext second,
      int instrumentCapacity,
      RetransmitTransport retransmitTransportA,
      RetransmitTransport retransmitTransportB,
      int retransmitRetries) {
    Objects.requireNonNull(first, "first");
    if (instrumentCapacity < 1) {
      throw new IllegalArgumentException("instrumentCapacity must be positive");
    }
    if (retransmitRetries < 0) {
      throw new IllegalArgumentException("retransmitRetries must be non-negative");
    }
    if (second == null) {
      context = first;
      peerContext = null;
    } else {
      Objects.requireNonNull(second, "second");
      if (first.gatewaySide() == second.gatewaySide()) {
        throw new IllegalArgumentException("redundant market data requires one A and one B context");
      }
      if (first.productGroup() != second.productGroup()) {
        throw new IllegalArgumentException("redundant market-data product groups must match");
      }
      context = first.gatewaySide() == GatewaySide.A ? first : second;
      peerContext = first.gatewaySide() == GatewaySide.B ? first : second;
    }
    if ((retransmitTransportA == null) != (retransmitTransportB == null)) {
      throw new IllegalArgumentException("both injected retransmit transports are required");
    }
    if (second == null && retransmitTransportA != null) {
      throw new IllegalArgumentException("injected A/B retransmit requires redundant contexts");
    }
    this.retransmitTransportA = retransmitTransportA;
    this.retransmitTransportB = retransmitTransportB;
    this.retransmitRetries = retransmitRetries;
    injectedRetransmitTransports = retransmitTransportA != null;
    orderBooks = new PrimitiveLongChannelRouter(instrumentCapacity);
    trades = new TradeChannelRouter(instrumentCapacity);
    instruments = new InstrumentRegistry(instrumentCapacity);
    reconstructedBooks = new OrderBookStateRouter(instrumentCapacity);
    dispatcher = new MarketDataPacketDispatcher(this::routeDecodedMessage);
    incrementalDispatcherA =
        new MarketDataPacketDispatcher(
            (buffer, offset, templateId, sequence) ->
                routeArbitratedIncremental(
                    FeedArbitrator.SOURCE_A, buffer, offset, templateId, sequence));
    incrementalDispatcherB =
        new MarketDataPacketDispatcher(
            (buffer, offset, templateId, sequence) ->
                routeArbitratedIncremental(
                    FeedArbitrator.SOURCE_B, buffer, offset, templateId, sequence));
    snapshotDispatcherA =
        new MarketDataPacketDispatcher(
            (buffer, offset, templateId, sequence) ->
                routeArbitratedSnapshot(
                    FeedArbitrator.SOURCE_A, buffer, offset, templateId, sequence));
    snapshotDispatcherB =
        new MarketDataPacketDispatcher(
            (buffer, offset, templateId, sequence) ->
                routeArbitratedSnapshot(
                    FeedArbitrator.SOURCE_B, buffer, offset, templateId, sequence));
    if (injectedRetransmitTransports) {
      initializeRetransmitClients();
    }
  }

  public StarbaseMarketDataContext context() {
    return context;
  }

  public StarbaseMarketDataContext context(GatewaySide side) {
    Objects.requireNonNull(side, "side");
    if (context.gatewaySide() == side) {
      return context;
    }
    if (peerContext != null && peerContext.gatewaySide() == side) {
      return peerContext;
    }
    throw new IllegalArgumentException("market-data side is not configured: " + side);
  }

  public boolean isRedundant() {
    return peerContext != null;
  }

  /** True only after one complete post-boundary Starbase snapshot cycle is atomic and live. */
  public boolean isSynchronized() {
    return isRedundant()
        && !freshSnapshotRequired
        && !assemblyFailure
        && snapshotRecoveryState == SNAPSHOT_LIVE
        && incrementalArbitrator.isInitialized()
        && reconstructedBooks.allReady();
  }

  /** Lifecycle readiness is stricter than transport connectivity. */
  public boolean isReady() {
    return isStarted() && hasUsableRedundantTransport() && isSynchronized();
  }

  public boolean requiresFreshSnapshot() {
    return freshSnapshotRequired;
  }

  public StarbaseLongChannel getOrderBookChannel(long instrumentId) {
    return orderBooks.getOrCreate(instrumentId);
  }

  public StarbaseTradeChannel getTradesChannel(long instrumentId) {
    return trades.getOrCreate(instrumentId);
  }

  public StarbaseLongChannel getReferenceDataChannel() {
    return referenceData;
  }

  public InstrumentRegistry instrumentRegistry() {
    return instruments;
  }

  public void configureOrderBook(
      long instrumentId, int orderCapacity, int levelCapacity) {
    boolean newState = reconstructedBooks.existing(instrumentId) == null;
    reconstructedBooks.configure(
        instrumentId, orderCapacity, levelCapacity, instruments, orderBooks);
    if (newState && isRedundant() && snapshotRecoveryState == SNAPSHOT_LIVE) {
      requireFreshSnapshot(context.clock().nanoTime());
    }
  }

  public void markOrderBookSnapshotComplete(long instrumentId) {
    reconstructedBooks.require(instrumentId).markSnapshotComplete();
  }

  public boolean isOrderBookReady(long instrumentId) {
    return reconstructedBooks.require(instrumentId).isReady();
  }

  public void invalidateOrderBook(long instrumentId, long timestampNanos) {
    reconstructedBooks.require(instrumentId).invalidate(timestampNanos);
  }

  public void onBookPut(
      long orderId,
      long instrumentId,
      int side,
      long quantity,
      long price,
      long sortOrderId,
      int flags,
      long sequenceNumber,
      long timestampNanos,
      boolean endOfCycle) {
    reconstructedBooks
        .require(instrumentId)
        .put(
            orderId,
            side,
            quantity,
            price,
            sortOrderId,
            flags,
            sequenceNumber,
            timestampNanos,
            endOfCycle);
  }

  public void onBookReduce(
      long orderId,
      long instrumentId,
      int side,
      long remainingQuantity,
      int flags,
      long sequenceNumber,
      long timestampNanos,
      boolean endOfCycle) {
    reconstructedBooks
        .require(instrumentId)
        .reduce(
            orderId,
            side,
            remainingQuantity,
            flags,
            sequenceNumber,
            timestampNanos,
            endOfCycle);
  }

  public void onBookDelete(
      long orderId,
      long instrumentId,
      int side,
      int flags,
      long sequenceNumber,
      long timestampNanos,
      boolean endOfCycle) {
    reconstructedBooks
        .require(instrumentId)
        .delete(
            orderId,
            side,
            flags,
            sequenceNumber,
            timestampNanos,
            endOfCycle);
  }

  /** Publishes a coherent primitive order-book event to an already cached channel. */
  public void publishOrderBook(
      long instrumentId, long value, long timestampNanos) {
    orderBooks.publishIfPresent(instrumentId, value, timestampNanos);
  }

  /** Publishes an authoritative primitive reference-data update. */
  public void publishReferenceData(
      long instrumentId, long value, long timestampNanos) {
    referenceData.publish(instrumentId, value, timestampNanos);
  }

  int acceptIncrementalPacket(GatewaySide side, ByteBuffer datagram) {
    return acceptFeedPacket(side, datagram, false);
  }

  int acceptSnapshotPacket(GatewaySide side, ByteBuffer datagram) {
    return acceptFeedPacket(side, datagram, true);
  }

  private synchronized int acceptFeedPacket(
      GatewaySide side, ByteBuffer datagram, boolean snapshot) {
    Objects.requireNonNull(datagram, "datagram");
    context(side);
    FeedDiagnostics diagnostics = diagnostics(side, snapshot);
    try {
      UdpPacketHeaderCodec.validate(datagram, 0);
      int expectedType =
          snapshot
              ? UdpPacketHeaderCodec.TYPE_SNAPSHOT
              : UdpPacketHeaderCodec.TYPE_INCREMENTAL_UPDATE;
      int actualType = UdpPacketHeaderCodec.type(datagram, 0);
      if (actualType != expectedType) {
        throw new StarbaseProtocolException(
            "unexpected live feed packet type: " + actualType);
      }
      long sequence = UdpPacketHeaderCodec.sequenceNumber(datagram, 0);
      int messageCount = UdpPacketHeaderCodec.messageCount(datagram, 0);
      FeedSequenceTracker tracker = sequenceTracker(side, snapshot);
      int transition = tracker.accept(sequence, messageCount);
      receivedPacketCount.incrementAndGet();
      diagnostics.onPacket(messageCount);
      if (transition == FeedSequenceTracker.DUPLICATE) {
        diagnostics.onDuplicate();
        return 0;
      }
      if (transition == FeedSequenceTracker.GAP) {
        diagnostics.onGap();
      }
      int dispatched = dispatcher(side, snapshot).dispatch(datagram, 0);
      if (transition == FeedSequenceTracker.GAP) {
        tracker.advanceAfterGap(sequence, messageCount);
      }
      return dispatched;
    } catch (StarbaseProtocolException failure) {
      if (isUnsupportedStateChange(datagram)) {
        diagnostics.onUnknownTemplate();
        failAssembly(safePacketTimestamp(datagram));
      } else {
        diagnostics.onCorruptFrame();
        requireFreshSnapshot(safePacketTimestamp(datagram));
      }
      throw failure;
    } catch (RuntimeException failure) {
      diagnostics.onCorruptFrame();
      requireFreshSnapshot(safePacketTimestamp(datagram));
      throw failure;
    }
  }

  private void routeArbitratedIncremental(
      int source,
      ByteBuffer buffer,
      int messageOffset,
      int templateId,
      long sequence) {
    FeedDiagnostics diagnostics = incrementalDiagnostics(source);
    int transition = incrementalArbitrator.accept(source, sequence);
    if (transition == FeedArbitrator.DUPLICATE) {
      diagnostics.onDuplicate();
      return;
    }
    if (transition == FeedArbitrator.GAP) {
      recoverIncrementalGap(
          source, buffer, messageOffset, templateId, sequence, diagnostics);
      return;
    }
    routeAssemblyIncremental(buffer, messageOffset, templateId, sequence);
  }

  private void recoverIncrementalGap(
      int source,
      ByteBuffer heldBuffer,
      int heldOffset,
      int heldTemplateId,
      long heldSequence,
      FeedDiagnostics diagnostics) {
    RetransmitClient client = retransmitClient(source);
    if (client == null) {
      requireFreshSnapshot(
          MarketDataMessageHeaderCodec.transactTimeNanos(heldBuffer, heldOffset));
      incrementalArbitrator.accept(source, heldSequence);
      return;
    }
    int result;
    try {
      result =
          client.recover(
              incrementalArbitrator.nextExpectedSequence(),
              incrementalArbitrator.gapSize());
    } catch (IOException failure) {
      transportFailure = failure;
      result = RetransmitClient.UNRECOVERABLE;
    }
    for (int request = 0; request < client.requestCount(); request++) {
      diagnostics.onRetransmitRequest();
    }
    if (client.failureReason() > RetransmitClient.FAILURE_NONE) {
      diagnostics.onRetransmitReject();
    }
    if (result == RetransmitClient.COMPLETE
        && incrementalArbitrator.nextExpectedSequence() == heldSequence) {
      diagnostics.onGapRecovered();
      int retry = incrementalArbitrator.accept(source, heldSequence);
      if (retry != FeedArbitrator.ACCEPTED) {
        throw new StarbaseProtocolException("held incremental did not follow retransmit range");
      }
      routeAssemblyIncremental(
          heldBuffer, heldOffset, heldTemplateId, heldSequence);
      return;
    }
    requireFreshSnapshot(
        MarketDataMessageHeaderCodec.transactTimeNanos(heldBuffer, heldOffset));
    incrementalArbitrator.accept(source, heldSequence);
  }

  private void routeRecoveredIncremental(
      int source,
      ByteBuffer buffer,
      int offset,
      int templateId,
      long sequence) {
    int transition = incrementalArbitrator.accept(source, sequence);
    if (transition != FeedArbitrator.ACCEPTED) {
      throw new StarbaseProtocolException(
          "retransmitted message is not the next missing sequence: " + sequence);
    }
    routeAssemblyIncremental(buffer, offset, templateId, sequence);
  }

  private void routeAssemblyIncremental(
      ByteBuffer buffer, int offset, int templateId, long sequence) {
    long timestamp = MarketDataMessageHeaderCodec.transactTimeNanos(buffer, offset);
    int flags = MarketDataMessageHeaderCodec.flags(buffer, offset);
    boolean recovering = freshSnapshotRequired || snapshotRecoveryState != SNAPSHOT_LIVE;
    switch (templateId) {
      case BidPutDecoder.TEMPLATE_ID -> {
        ReconstructedOrderBookState state =
            reconstructedBooks.existing(BidPutDecoder.instrumentId(buffer, offset));
        if (state != null) {
          if (recovering) {
            state.incrementalPutDuringRecovery(
                sequence,
                BidPutDecoder.orderId(buffer, offset),
                BidPutDecoder.SIDE,
                BidPutDecoder.quantityMantissa(buffer, offset),
                BidPutDecoder.priceMantissa(buffer, offset),
                BidPutDecoder.sortOrderId(buffer, offset),
                flags,
                timestamp);
          } else {
            routeBidPut(buffer, offset, sequence, timestamp);
          }
        }
      }
      case AskPutDecoder.TEMPLATE_ID -> {
        ReconstructedOrderBookState state =
            reconstructedBooks.existing(AskPutDecoder.instrumentId(buffer, offset));
        if (state != null) {
          if (recovering) {
            state.incrementalPutDuringRecovery(
                sequence,
                AskPutDecoder.orderId(buffer, offset),
                AskPutDecoder.SIDE,
                AskPutDecoder.quantityMantissa(buffer, offset),
                AskPutDecoder.priceMantissa(buffer, offset),
                AskPutDecoder.sortOrderId(buffer, offset),
                flags,
                timestamp);
          } else {
            routeAskPut(buffer, offset, sequence, timestamp);
          }
        }
      }
      case BidQtyReducedDecoder.TEMPLATE_ID ->
          routeRecoveryReduce(buffer, offset, sequence, timestamp, flags, true, recovering);
      case AskQtyReducedDecoder.TEMPLATE_ID ->
          routeRecoveryReduce(buffer, offset, sequence, timestamp, flags, false, recovering);
      case BidDeleteDecoder.TEMPLATE_ID ->
          routeRecoveryDelete(buffer, offset, sequence, timestamp, flags, true, recovering);
      case AskDeleteDecoder.TEMPLATE_ID ->
          routeRecoveryDelete(buffer, offset, sequence, timestamp, flags, false, recovering);
      case EndOfCycleDecoder.TEMPLATE_ID -> {
        if (recovering) {
          reconstructedBooks.incrementalEndOfCycleDuringRecovery();
        } else {
          reconstructedBooks.onEndOfCycle(flags, sequence, timestamp);
          tradeSummary.onEndOfCycle();
        }
      }
      default -> {
        if (!recovering || isReferenceTemplate(templateId)) {
          routeDecodedMessage(buffer, offset, templateId, sequence);
        }
      }
    }
  }

  private void routeRecoveryReduce(
      ByteBuffer buffer,
      int offset,
      long sequence,
      long timestamp,
      int flags,
      boolean bid,
      boolean recovering) {
    long instrumentId =
        bid
            ? BidQtyReducedDecoder.instrumentId(buffer, offset)
            : AskQtyReducedDecoder.instrumentId(buffer, offset);
    ReconstructedOrderBookState state = reconstructedBooks.existing(instrumentId);
    if (state == null) {
      return;
    }
    if (recovering) {
      state.incrementalReduceDuringRecovery(
          sequence,
          bid
              ? BidQtyReducedDecoder.orderId(buffer, offset)
              : AskQtyReducedDecoder.orderId(buffer, offset),
          bid ? BidQtyReducedDecoder.SIDE : AskQtyReducedDecoder.SIDE,
          bid
              ? BidQtyReducedDecoder.quantityMantissa(buffer, offset)
              : AskQtyReducedDecoder.quantityMantissa(buffer, offset),
          flags,
          timestamp);
    } else if (bid) {
      routeBidReduce(buffer, offset, sequence, timestamp);
    } else {
      routeAskReduce(buffer, offset, sequence, timestamp);
    }
  }

  private void routeRecoveryDelete(
      ByteBuffer buffer,
      int offset,
      long sequence,
      long timestamp,
      int flags,
      boolean bid,
      boolean recovering) {
    long instrumentId =
        bid
            ? BidDeleteDecoder.instrumentId(buffer, offset)
            : AskDeleteDecoder.instrumentId(buffer, offset);
    ReconstructedOrderBookState state = reconstructedBooks.existing(instrumentId);
    if (state == null) {
      return;
    }
    if (recovering) {
      state.incrementalDeleteDuringRecovery(
          sequence,
          bid
              ? BidDeleteDecoder.orderId(buffer, offset)
              : AskDeleteDecoder.orderId(buffer, offset),
          bid ? BidDeleteDecoder.SIDE : AskDeleteDecoder.SIDE,
          flags,
          timestamp);
    } else if (bid) {
      routeBidDelete(buffer, offset, sequence, timestamp);
    } else {
      routeAskDelete(buffer, offset, sequence, timestamp);
    }
  }

  private void routeArbitratedSnapshot(
      int source,
      ByteBuffer buffer,
      int messageOffset,
      int templateId,
      long sequence) {
    FeedDiagnostics diagnostics = snapshotDiagnostics(source);
    int transition = snapshotArbitrator.accept(source, sequence);
    if (transition == FeedArbitrator.DUPLICATE) {
      diagnostics.onDuplicate();
      return;
    }
    if (transition == FeedArbitrator.GAP) {
      diagnostics.onGap();
      requireFreshSnapshot(
          MarketDataMessageHeaderCodec.transactTimeNanos(buffer, messageOffset));
      snapshotArbitrator.reset();
      snapshotArbitrator.accept(source, sequence);
      return;
    }
    routeAssemblySnapshot(buffer, messageOffset, templateId, sequence);
  }

  private void routeAssemblySnapshot(
      ByteBuffer buffer, int offset, int templateId, long sequence) {
    long timestamp = MarketDataMessageHeaderCodec.transactTimeNanos(buffer, offset);
    if (templateId == EndOfCycleDecoder.TEMPLATE_ID) {
      onAssemblySnapshotCycleEnd(timestamp);
      return;
    }
    if (snapshotRecoveryState != SNAPSHOT_COLLECTING) {
      return;
    }
    switch (templateId) {
      case SnapshotHeaderDecoder.TEMPLATE_ID -> {
        if (assemblySnapshotOpen) {
          throw new StarbaseProtocolException("nested assembly SnapshotHeader");
        }
        assemblySnapshotOpen = true;
        assemblySnapshotStateOpen = false;
        assemblySnapshotInstrumentId = SnapshotHeaderDecoder.instrumentId(buffer, offset);
        assemblySnapshotAnchor =
            SnapshotHeaderDecoder.incrementalSequenceNumber(buffer, offset);
        beginAssemblyBookSnapshotIfConfigured();
      }
      case SnapshotTrailerDecoder.TEMPLATE_ID -> {
        long instrumentId = SnapshotTrailerDecoder.instrumentId(buffer, offset);
        long anchor = SnapshotTrailerDecoder.incrementalSequenceNumber(buffer, offset);
        if (!assemblySnapshotOpen
            || instrumentId != assemblySnapshotInstrumentId
            || anchor != assemblySnapshotAnchor) {
          throw new StarbaseProtocolException(
              "assembly SnapshotTrailer does not match its header");
        }
        if (assemblySnapshotStateOpen) {
          reconstructedBooks.require(instrumentId).completeAtomicSnapshot(anchor);
        }
        assemblySnapshotOpen = false;
        assemblySnapshotStateOpen = false;
      }
      case BidPutDecoder.TEMPLATE_ID ->
          applyAssemblySnapshotPut(buffer, offset, true);
      case AskPutDecoder.TEMPLATE_ID ->
          applyAssemblySnapshotPut(buffer, offset, false);
      case BidQtyReducedDecoder.TEMPLATE_ID,
          AskQtyReducedDecoder.TEMPLATE_ID,
          BidDeleteDecoder.TEMPLATE_ID,
          AskDeleteDecoder.TEMPLATE_ID ->
          throw new StarbaseProtocolException(
              "unsupported mutation inside Starbase snapshot: " + templateId);
      default -> {
        routeDecodedMessage(buffer, offset, templateId, sequence);
        if (templateId == InstrumentDefinitionDecoder.TEMPLATE_ID) {
          beginAssemblyBookSnapshotIfConfigured();
        }
      }
    }
  }

  private void applyAssemblySnapshotPut(
      ByteBuffer buffer, int offset, boolean bid) {
    long instrumentId =
        bid
            ? BidPutDecoder.instrumentId(buffer, offset)
            : AskPutDecoder.instrumentId(buffer, offset);
    if (!assemblySnapshotOpen || instrumentId != assemblySnapshotInstrumentId) {
      throw new StarbaseProtocolException(
          "snapshot book mutation is outside its instrument boundary");
    }
    if (!assemblySnapshotStateOpen) {
      return;
    }
    ReconstructedOrderBookState state = reconstructedBooks.require(instrumentId);
    state.atomicSnapshotPut(
        bid ? BidPutDecoder.orderId(buffer, offset) : AskPutDecoder.orderId(buffer, offset),
        bid ? BidPutDecoder.SIDE : AskPutDecoder.SIDE,
        bid
            ? BidPutDecoder.quantityMantissa(buffer, offset)
            : AskPutDecoder.quantityMantissa(buffer, offset),
        bid
            ? BidPutDecoder.priceMantissa(buffer, offset)
            : AskPutDecoder.priceMantissa(buffer, offset),
        bid
            ? BidPutDecoder.sortOrderId(buffer, offset)
            : AskPutDecoder.sortOrderId(buffer, offset));
  }

  private void beginAssemblyBookSnapshotIfConfigured() {
    if (!assemblySnapshotOpen || assemblySnapshotStateOpen) {
      return;
    }
    ReconstructedOrderBookState state =
        reconstructedBooks.existing(assemblySnapshotInstrumentId);
    if (state != null) {
      state.beginAtomicSnapshot(assemblySnapshotAnchor, snapshotGeneration);
      assemblySnapshotStateOpen = true;
    }
  }

  private void onAssemblySnapshotCycleEnd(long timestamp) {
    if (snapshotRecoveryState == SNAPSHOT_WAITING_FOR_BOUNDARY) {
      snapshotGeneration = incrementGeneration(snapshotGeneration);
      snapshotRecoveryState = SNAPSHOT_COLLECTING;
      assemblySnapshotOpen = false;
      assemblySnapshotStateOpen = false;
      snapshotDiagnostics.onSnapshotReset();
      peerSnapshotDiagnostics.onSnapshotReset();
      return;
    }
    if (snapshotRecoveryState != SNAPSHOT_COLLECTING) {
      return;
    }
    if (assemblySnapshotOpen
        || !reconstructedBooks.hasAtomicSnapshots(snapshotGeneration)
        || !reconstructedBooks.activateAtomicSnapshots(snapshotGeneration, timestamp)) {
      requireFreshSnapshot(timestamp);
      return;
    }
    freshSnapshotRequired = false;
    snapshotRecoveryState = SNAPSHOT_LIVE;
    snapshotDiagnostics.onSnapshotComplete();
    peerSnapshotDiagnostics.onSnapshotComplete();
  }

  private void requireFreshSnapshot(long timestamp) {
    freshSnapshotRequired = true;
    snapshotRecoveryState = SNAPSHOT_WAITING_FOR_BOUNDARY;
    assemblySnapshotOpen = false;
    assemblySnapshotStateOpen = false;
    reconstructedBooks.abandonAtomicSnapshots(timestamp);
    incrementalArbitrator.reset();
  }

  private void failAssembly(long timestamp) {
    assemblyFailure = true;
    requireFreshSnapshot(timestamp);
  }

  private void initializeRetransmitClients() {
    retransmitClientA =
        new RetransmitClient(
            retransmitTransportA,
            context.clock(),
            context.retransmitTimeout(),
            retransmitRetries,
            context.receiveBufferBytes(),
            (buffer, offset, templateId, sequence) ->
                routeRecoveredIncremental(
                    FeedArbitrator.SOURCE_A,
                    buffer,
                    offset,
                    templateId,
                    sequence));
    retransmitClientB =
        new RetransmitClient(
            retransmitTransportB,
            peerContext.clock(),
            peerContext.retransmitTimeout(),
            retransmitRetries,
            peerContext.receiveBufferBytes(),
            (buffer, offset, templateId, sequence) ->
                routeRecoveredIncremental(
                    FeedArbitrator.SOURCE_B,
                    buffer,
                    offset,
                    templateId,
                    sequence));
  }

  private MarketDataPacketDispatcher dispatcher(GatewaySide side, boolean snapshot) {
    if (side == GatewaySide.A) {
      return snapshot ? snapshotDispatcherA : incrementalDispatcherA;
    }
    return snapshot ? snapshotDispatcherB : incrementalDispatcherB;
  }

  private FeedSequenceTracker sequenceTracker(GatewaySide side, boolean snapshot) {
    if (side == context.gatewaySide()) {
      return snapshot ? snapshotSequence : incrementalSequence;
    }
    return snapshot ? peerSnapshotSequence : peerIncrementalSequence;
  }

  private FeedDiagnostics diagnostics(GatewaySide side, boolean snapshot) {
    if (side == context.gatewaySide()) {
      return snapshot ? snapshotDiagnostics : incrementalDiagnostics;
    }
    return snapshot ? peerSnapshotDiagnostics : peerIncrementalDiagnostics;
  }

  private FeedDiagnostics incrementalDiagnostics(int source) {
    return source == FeedArbitrator.SOURCE_A
        ? diagnostics(GatewaySide.A, false)
        : diagnostics(GatewaySide.B, false);
  }

  private FeedDiagnostics snapshotDiagnostics(int source) {
    return source == FeedArbitrator.SOURCE_A
        ? diagnostics(GatewaySide.A, true)
        : diagnostics(GatewaySide.B, true);
  }

  private RetransmitClient retransmitClient(int source) {
    return source == FeedArbitrator.SOURCE_A ? retransmitClientA : retransmitClientB;
  }

  private static boolean isReferenceTemplate(int templateId) {
    return templateId == InstrumentDefinitionDecoder.TEMPLATE_ID
        || templateId == InstrumentStatusUpdateDecoder.TEMPLATE_ID
        || templateId == InstrumentInfoDecoder.TEMPLATE_ID
        || templateId == InstrumentRefDecoder.TEMPLATE_ID
        || templateId == 11
        || templateId == 12;
  }

  private static boolean isUnsupportedStateChange(ByteBuffer datagram) {
    if (datagram.limit()
        < UdpPacketHeaderCodec.ENCODED_LENGTH
            + MarketDataMessageHeaderCodec.ENCODED_LENGTH) {
      return false;
    }
    int messageOffset = UdpPacketHeaderCodec.ENCODED_LENGTH;
    int templateId = MarketDataMessageHeaderCodec.templateId(datagram, messageOffset);
    return templateId == 33;
  }

  private static long safePacketTimestamp(ByteBuffer datagram) {
    if (datagram.limit() >= UdpPacketHeaderCodec.ENCODED_LENGTH) {
      return Math.max(0L, UdpPacketHeaderCodec.sendingTimeNanos(datagram, 0));
    }
    return 0L;
  }

  private static long incrementGeneration(long generation) {
    if (generation == Long.MAX_VALUE) {
      throw new StarbaseProtocolException("snapshot generation overflow");
    }
    return generation + 1;
  }

  void routeDecodedMessage(
      ByteBuffer buffer, int messageOffset, int templateId, long sequenceNumber) {
    long timestampNanos =
        MarketDataMessageHeaderCodec.transactTimeNanos(buffer, messageOffset);
    switch (templateId) {
      case InstrumentDefinitionDecoder.TEMPLATE_ID -> {
        instruments.applyDefinition(buffer, messageOffset, context.productGroup());
        long instrumentId =
            InstrumentDefinitionDecoder.instrumentId(buffer, messageOffset);
        publishReferenceData(instrumentId, templateId, timestampNanos);
        if (orderBooks.existing(instrumentId) != null
            && reconstructedBooks.existing(instrumentId) == null) {
          ReconstructedOrderBookState state =
              reconstructedBooks.configure(
                  instrumentId,
                  DEFAULT_BOOK_ORDER_CAPACITY,
                  DEFAULT_BOOK_LEVEL_CAPACITY,
                  instruments,
                  orderBooks);
          if (replaySnapshotOpen && replaySnapshotInstrumentId == instrumentId) {
            state.beginSnapshot(
                replaySnapshotFlags,
                replaySnapshotSequence,
                replaySnapshotTimestamp);
          } else if (isRedundant() && snapshotRecoveryState == SNAPSHOT_LIVE) {
            requireFreshSnapshot(timestampNanos);
          }
        }
      }
      case IndexDefinitionDecoder.TEMPLATE_ID ->
          publishReferenceData(
              IndexDefinitionDecoder.indexId(buffer, messageOffset),
              templateId,
              timestampNanos);
      case IndexInfoDecoder.TEMPLATE_ID ->
          publishReferenceData(
              IndexInfoDecoder.indexId(buffer, messageOffset),
              templateId,
              timestampNanos);
      case InstrumentStatusUpdateDecoder.TEMPLATE_ID -> {
        instruments.applyStatus(buffer, messageOffset);
        publishReferenceData(
            InstrumentStatusUpdateDecoder.instrumentId(buffer, messageOffset),
            templateId,
            timestampNanos);
      }
      case InstrumentInfoDecoder.TEMPLATE_ID ->
          publishReferenceData(
              InstrumentInfoDecoder.instrumentId(buffer, messageOffset),
              templateId,
              timestampNanos);
      case InstrumentRefDecoder.TEMPLATE_ID ->
          publishReferenceData(
              InstrumentRefDecoder.instrumentId(buffer, messageOffset),
              templateId,
              timestampNanos);
      case BidPutDecoder.TEMPLATE_ID ->
          routeBidPut(buffer, messageOffset, sequenceNumber, timestampNanos);
      case AskPutDecoder.TEMPLATE_ID ->
          routeAskPut(buffer, messageOffset, sequenceNumber, timestampNanos);
      case BidQtyReducedDecoder.TEMPLATE_ID ->
          routeBidReduce(buffer, messageOffset, sequenceNumber, timestampNanos);
      case AskQtyReducedDecoder.TEMPLATE_ID ->
          routeAskReduce(buffer, messageOffset, sequenceNumber, timestampNanos);
      case BidDeleteDecoder.TEMPLATE_ID ->
          routeBidDelete(buffer, messageOffset, sequenceNumber, timestampNanos);
      case AskDeleteDecoder.TEMPLATE_ID ->
          routeAskDelete(buffer, messageOffset, sequenceNumber, timestampNanos);
      case EndOfCycleDecoder.TEMPLATE_ID -> {
          reconstructedBooks.onEndOfCycle(
              MarketDataMessageHeaderCodec.flags(buffer, messageOffset),
              sequenceNumber,
              timestampNanos);
        tradeSummary.onEndOfCycle();
      }
      case TradeSummaryDecoder.TEMPLATE_ID ->
          tradeSummary.onSummary(buffer, messageOffset);
      case TradeDecoder.TEMPLATE_ID ->
          tradeSummary.onTrade(
              buffer,
              messageOffset,
              sequenceNumber,
              timestampNanos,
              trades);
      case SnapshotHeaderDecoder.TEMPLATE_ID ->
          onSnapshotHeader(buffer, messageOffset, sequenceNumber, timestampNanos);
      case SnapshotTrailerDecoder.TEMPLATE_ID ->
          onSnapshotTrailer(buffer, messageOffset, sequenceNumber, timestampNanos);
      default -> {
        // Codec coverage exceeds the channel routes currently assembled by this API.
      }
    }
  }

  int dispatchReplayPacket(ByteBuffer datagram) {
    return dispatcher.dispatch(datagram, 0);
  }

  private void onSnapshotHeader(
      ByteBuffer buffer, int offset, long sequence, long timestamp) {
    if (replaySnapshotOpen) {
      throw new io.contek.invoker.deribit.starbase.common.StarbaseProtocolException(
          "nested stateful SnapshotHeader");
    }
    replaySnapshotOpen = true;
    replaySnapshotInstrumentId = SnapshotHeaderDecoder.instrumentId(buffer, offset);
    replaySnapshotFlags = MarketDataMessageHeaderCodec.flags(buffer, offset);
    replaySnapshotSequence = sequence;
    replaySnapshotTimestamp = timestamp;
    ReconstructedOrderBookState state =
        reconstructedBooks.existing(replaySnapshotInstrumentId);
    if (state != null) {
      state.beginSnapshot(replaySnapshotFlags, sequence, timestamp);
    }
  }

  private void onSnapshotTrailer(
      ByteBuffer buffer, int offset, long sequence, long timestamp) {
    long instrumentId = SnapshotTrailerDecoder.instrumentId(buffer, offset);
    if (!replaySnapshotOpen || replaySnapshotInstrumentId != instrumentId) {
      throw new io.contek.invoker.deribit.starbase.common.StarbaseProtocolException(
          "stateful SnapshotTrailer does not match header");
    }
    ReconstructedOrderBookState state = reconstructedBooks.existing(instrumentId);
    if (state != null) {
      state.completeSnapshot(
          MarketDataMessageHeaderCodec.flags(buffer, offset), sequence, timestamp);
    }
    replaySnapshotOpen = false;
  }

  private void routeBidPut(
      ByteBuffer buffer, int offset, long sequence, long timestamp) {
    routePut(
        buffer,
        offset,
        sequence,
        timestamp,
        BidPutDecoder.instrumentId(buffer, offset),
        BidPutDecoder.orderId(buffer, offset),
        BidPutDecoder.SIDE,
        BidPutDecoder.quantityMantissa(buffer, offset),
        BidPutDecoder.priceMantissa(buffer, offset),
        BidPutDecoder.sortOrderId(buffer, offset));
  }

  private void routeAskPut(
      ByteBuffer buffer, int offset, long sequence, long timestamp) {
    routePut(
        buffer,
        offset,
        sequence,
        timestamp,
        AskPutDecoder.instrumentId(buffer, offset),
        AskPutDecoder.orderId(buffer, offset),
        AskPutDecoder.SIDE,
        AskPutDecoder.quantityMantissa(buffer, offset),
        AskPutDecoder.priceMantissa(buffer, offset),
        AskPutDecoder.sortOrderId(buffer, offset));
  }

  private void routePut(
      ByteBuffer buffer,
      int offset,
      long sequence,
      long timestamp,
      long instrumentId,
      long orderId,
      int side,
      long quantity,
      long price,
      long sortOrderId) {
    ReconstructedOrderBookState state = reconstructedBooks.existing(instrumentId);
    if (state != null) {
      state.put(
          orderId,
          side,
          quantity,
          price,
          sortOrderId,
          MarketDataMessageHeaderCodec.flags(buffer, offset),
          sequence,
          timestamp,
          false);
    }
  }

  private void routeBidReduce(
      ByteBuffer buffer, int offset, long sequence, long timestamp) {
    routeReduce(
        buffer,
        offset,
        sequence,
        timestamp,
        BidQtyReducedDecoder.instrumentId(buffer, offset),
        BidQtyReducedDecoder.orderId(buffer, offset),
        BidQtyReducedDecoder.SIDE,
        BidQtyReducedDecoder.quantityMantissa(buffer, offset));
  }

  private void routeAskReduce(
      ByteBuffer buffer, int offset, long sequence, long timestamp) {
    routeReduce(
        buffer,
        offset,
        sequence,
        timestamp,
        AskQtyReducedDecoder.instrumentId(buffer, offset),
        AskQtyReducedDecoder.orderId(buffer, offset),
        AskQtyReducedDecoder.SIDE,
        AskQtyReducedDecoder.quantityMantissa(buffer, offset));
  }

  private void routeReduce(
      ByteBuffer buffer,
      int offset,
      long sequence,
      long timestamp,
      long instrumentId,
      long orderId,
      int side,
      long quantity) {
    ReconstructedOrderBookState state = reconstructedBooks.existing(instrumentId);
    if (state != null) {
      state.reduce(
          orderId,
          side,
          quantity,
          MarketDataMessageHeaderCodec.flags(buffer, offset),
          sequence,
          timestamp,
          false);
    }
  }

  private void routeBidDelete(
      ByteBuffer buffer, int offset, long sequence, long timestamp) {
    routeDelete(
        buffer,
        offset,
        sequence,
        timestamp,
        BidDeleteDecoder.instrumentId(buffer, offset),
        BidDeleteDecoder.orderId(buffer, offset),
        BidDeleteDecoder.SIDE);
  }

  private void routeAskDelete(
      ByteBuffer buffer, int offset, long sequence, long timestamp) {
    routeDelete(
        buffer,
        offset,
        sequence,
        timestamp,
        AskDeleteDecoder.instrumentId(buffer, offset),
        AskDeleteDecoder.orderId(buffer, offset),
        AskDeleteDecoder.SIDE);
  }

  private void routeDelete(
      ByteBuffer buffer,
      int offset,
      long sequence,
      long timestamp,
      long instrumentId,
      long orderId,
      int side) {
    ReconstructedOrderBookState state = reconstructedBooks.existing(instrumentId);
    if (state != null) {
      state.delete(
          orderId,
          side,
          MarketDataMessageHeaderCodec.flags(buffer, offset),
          sequence,
          timestamp,
          false);
    }
  }

  public boolean isTransportOpen() {
    MarketDataUdpReceiver incremental = incrementalReceiver;
    MarketDataUdpReceiver snapshot = snapshotReceiver;
    boolean primaryOpen = incremental != null
        && snapshot != null
        && incremental.isOpen()
        && snapshot.isOpen()
        && transportRunning;
    if (!primaryOpen || peerContext == null) {
      return primaryOpen;
    }
    MarketDataUdpReceiver peerIncremental = peerIncrementalReceiver;
    MarketDataUdpReceiver peerSnapshot = peerSnapshotReceiver;
    return peerIncremental != null
        && peerSnapshot != null
        && peerIncremental.isOpen()
        && peerSnapshot.isOpen();
  }

  private boolean hasUsableRedundantTransport() {
    if (!transportRunning || peerContext == null) {
      return false;
    }
    return (isOpen(incrementalReceiver) || isOpen(peerIncrementalReceiver))
        && (isOpen(snapshotReceiver) || isOpen(peerSnapshotReceiver));
  }

  public int receiverLoopCount() {
    int count = 0;
    Thread incremental = incrementalThread;
    Thread snapshot = snapshotThread;
    if (incremental != null && incremental.isAlive()) {
      count++;
    }
    if (snapshot != null && snapshot.isAlive()) {
      count++;
    }
    Thread peerIncremental = peerIncrementalThread;
    Thread peerSnapshot = peerSnapshotThread;
    if (peerIncremental != null && peerIncremental.isAlive()) {
      count++;
    }
    if (peerSnapshot != null && peerSnapshot.isAlive()) {
      count++;
    }
    return count;
  }

  public long receivedPacketCount() {
    return receivedPacketCount.get();
  }

  public Throwable transportFailure() {
    return transportFailure;
  }

  public long incrementalNextExpectedSequence() {
    return incrementalSequence.nextExpectedSequence();
  }

  public long snapshotNextExpectedSequence() {
    return snapshotSequence.nextExpectedSequence();
  }

  public FeedDiagnostics incrementalDiagnostics() {
    return incrementalDiagnostics;
  }

  public FeedDiagnostics incrementalDiagnostics(GatewaySide side) {
    context(side);
    return diagnostics(side, false);
  }

  public FeedDiagnostics snapshotDiagnostics() {
    return snapshotDiagnostics;
  }

  public FeedDiagnostics snapshotDiagnostics(GatewaySide side) {
    context(side);
    return diagnostics(side, true);
  }

  @Override
  protected void onStart() {
    try {
      incrementalSequence.reset();
      snapshotSequence.reset();
      peerIncrementalSequence.reset();
      peerSnapshotSequence.reset();
      incrementalArbitrator.reset();
      snapshotArbitrator.reset();
      transportFailure = null;
      assemblyFailure = false;
      freshSnapshotRequired = true;
      snapshotRecoveryState = SNAPSHOT_WAITING_FOR_BOUNDARY;
      if (isRedundant() && !injectedRetransmitTransports) {
        retransmitTransportA =
            UdpRetransmitTransport.open(
                context.retransmitEndpoint(),
                context.sendBufferBytes(),
                context.receiveBufferBytes());
        retransmitTransportB =
            UdpRetransmitTransport.open(
                peerContext.retransmitEndpoint(),
                peerContext.sendBufferBytes(),
                peerContext.receiveBufferBytes());
        initializeRetransmitClients();
      }
      incrementalReceiver =
          openReceiver(
              context,
              context.incrementalGroup(),
              incrementalSequence,
              incrementalDiagnostics,
              false);
      snapshotReceiver =
          openReceiver(
              context,
              context.snapshotGroup(),
              snapshotSequence,
              snapshotDiagnostics,
              true);
      if (peerContext != null) {
        peerIncrementalReceiver =
            openReceiver(
                peerContext,
                peerContext.incrementalGroup(),
                peerIncrementalSequence,
                peerIncrementalDiagnostics,
                false);
        peerSnapshotReceiver =
            openReceiver(
                peerContext,
                peerContext.snapshotGroup(),
                peerSnapshotSequence,
                peerSnapshotDiagnostics,
                true);
      }
      incrementalDiagnostics.onTransportOpen();
      snapshotDiagnostics.onTransportOpen();
      if (peerContext != null) {
        peerIncrementalDiagnostics.onTransportOpen();
        peerSnapshotDiagnostics.onTransportOpen();
      }
      transportRunning = true;
      incrementalThread =
          startReceiverThread(
              context, "incremental", incrementalReceiver, incrementalDiagnostics);
      snapshotThread =
          startReceiverThread(
              context, "snapshot", snapshotReceiver, snapshotDiagnostics);
      if (peerContext != null) {
        peerIncrementalThread =
            startReceiverThread(
                peerContext,
                "incremental",
                peerIncrementalReceiver,
                peerIncrementalDiagnostics);
        peerSnapshotThread =
            startReceiverThread(
                peerContext,
                "snapshot",
                peerSnapshotReceiver,
                peerSnapshotDiagnostics);
      }
    } catch (IOException | RuntimeException failure) {
      transportRunning = false;
      closeReceiver(incrementalReceiver);
      closeReceiver(snapshotReceiver);
      closeReceiver(peerIncrementalReceiver);
      closeReceiver(peerSnapshotReceiver);
      closeRetransmitTransport(retransmitTransportA);
      closeRetransmitTransport(retransmitTransportB);
      incrementalReceiver = null;
      snapshotReceiver = null;
      peerIncrementalReceiver = null;
      peerSnapshotReceiver = null;
      throw new StarbaseException("failed to start Starbase market-data transport", failure);
    }
  }

  @Override
  protected void onClose() {
    transportRunning = false;
    closeReceiver(incrementalReceiver);
    closeReceiver(snapshotReceiver);
    closeReceiver(peerIncrementalReceiver);
    closeReceiver(peerSnapshotReceiver);
    joinReceiver(incrementalThread);
    joinReceiver(snapshotThread);
    joinReceiver(peerIncrementalThread);
    joinReceiver(peerSnapshotThread);
    closeRetransmitTransport(retransmitTransportA);
    closeRetransmitTransport(retransmitTransportB);
    incrementalDiagnostics.onTransportClosed();
    snapshotDiagnostics.onTransportClosed();
    if (peerContext != null) {
      peerIncrementalDiagnostics.onTransportClosed();
      peerSnapshotDiagnostics.onTransportClosed();
    }
    incrementalReceiver = null;
    snapshotReceiver = null;
    peerIncrementalReceiver = null;
    peerSnapshotReceiver = null;
    incrementalThread = null;
    snapshotThread = null;
    peerIncrementalThread = null;
    peerSnapshotThread = null;
  }

  private MarketDataUdpReceiver openReceiver(
      StarbaseMarketDataContext receiverContext,
      java.net.InetSocketAddress endpoint,
      FeedSequenceTracker sequenceTracker,
      FeedDiagnostics diagnostics,
      boolean snapshot)
      throws IOException {
    return MarketDataUdpReceiver.open(
        endpoint,
        receiverContext.networkInterfaceName(),
        receiverContext.receiveBufferBytes(),
        receiverContext.ioPolicy(),
        isRedundant()
            ? (buffer, length) ->
                acceptFeedPacket(receiverContext.gatewaySide(), buffer, snapshot)
            : (buffer, length) -> handlePacket(buffer, sequenceTracker, diagnostics));
  }

  private void handlePacket(
      java.nio.ByteBuffer buffer,
      FeedSequenceTracker sequenceTracker,
      FeedDiagnostics diagnostics) {
    UdpPacketHeaderCodec.validate(buffer, 0);
    long sequence = UdpPacketHeaderCodec.sequenceNumber(buffer, 0);
    int messageCount = UdpPacketHeaderCodec.messageCount(buffer, 0);
    int transition = sequenceTracker.accept(sequence, messageCount);
    receivedPacketCount.incrementAndGet();
    diagnostics.onPacket(messageCount);
    if (transition == FeedSequenceTracker.DUPLICATE) {
      diagnostics.onDuplicate();
      return;
    }
    if (transition == FeedSequenceTracker.GAP) {
      diagnostics.onGap();
      return;
    }
    dispatcher.dispatch(buffer, 0);
  }

  private Thread startReceiverThread(
      StarbaseMarketDataContext receiverContext,
      String feed,
      MarketDataUdpReceiver receiver,
      FeedDiagnostics diagnostics) {
    Thread thread =
        Thread.ofPlatform()
            .name(
                "starbase-md-"
                    + receiverContext.productGroup().name().toLowerCase()
                    + "-"
                    + receiverContext.gatewaySide().name().toLowerCase()
                    + "-"
                    + feed)
            .daemon(true)
            .unstarted(() -> runReceiver(receiver, diagnostics));
    thread.start();
    return thread;
  }

  private void runReceiver(
      MarketDataUdpReceiver receiver, FeedDiagnostics diagnostics) {
    try {
      while (transportRunning) {
        int length = receiver.receive();
        if (length == 0) {
          Thread.onSpinWait();
        }
      }
    } catch (IOException | RuntimeException failure) {
      if (transportRunning) {
        onReceiverFailure(receiver, diagnostics, failure);
      }
    }
  }

  private synchronized void onReceiverFailure(
      MarketDataUdpReceiver receiver,
      FeedDiagnostics diagnostics,
      Throwable failure) {
    transportFailure = failure;
    if (diagnostics.health() != FeedDiagnostics.UNHEALTHY) {
      diagnostics.onCorruptFrame();
    }
    if (isRedundant()) {
      closeReceiver(receiver);
      return;
    }
    transportRunning = false;
    closeReceiver(incrementalReceiver);
    closeReceiver(snapshotReceiver);
  }

  private static void closeReceiver(MarketDataUdpReceiver receiver) {
    if (receiver != null) {
      try {
        receiver.close();
      } catch (IOException ignored) {
        // Closing is best-effort; any receive-side failure remains available via transportFailure.
      }
    }
  }

  private static boolean isOpen(MarketDataUdpReceiver receiver) {
    return receiver != null && receiver.isOpen();
  }

  private static void closeRetransmitTransport(RetransmitTransport transport) {
    if (transport instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception ignored) {
        // Receive-side failure remains available from transportFailure.
      }
    }
  }

  private static void joinReceiver(Thread thread) {
    if (thread == null || thread == Thread.currentThread()) {
      return;
    }
    boolean interrupted = false;
    while (thread.isAlive()) {
      try {
        thread.join();
      } catch (InterruptedException exception) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
