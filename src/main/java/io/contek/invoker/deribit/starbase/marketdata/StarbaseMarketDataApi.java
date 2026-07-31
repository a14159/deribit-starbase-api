package io.contek.invoker.deribit.starbase.marketdata;

import io.contek.invoker.deribit.starbase.channel.StarbaseLongChannel;
import io.contek.invoker.deribit.starbase.channel.PrimitiveLongChannelRouter;
import io.contek.invoker.deribit.starbase.channel.StarbaseTradeChannel;
import io.contek.invoker.deribit.starbase.book.InstrumentRegistry;
import io.contek.invoker.deribit.starbase.codec.marketdata.MarketDataPacketDispatcher;
import io.contek.invoker.deribit.starbase.codec.marketdata.InstrumentDefinitionDecoder;
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
import io.contek.invoker.deribit.starbase.common.StarbaseException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-side UDP receiver and channel API.
 *
 * <p>The separately tested A/B arbitration and retransmit components are not yet composed
 * into this public lifecycle; see {@code docs/implementation-status.md}.
 */
public final class StarbaseMarketDataApi extends AbstractStarbaseApi {

  private static final int DEFAULT_INSTRUMENT_CAPACITY = 4096;
  private static final int DEFAULT_BOOK_ORDER_CAPACITY = 65_536;
  private static final int DEFAULT_BOOK_LEVEL_CAPACITY = 65_536;

  private final StarbaseMarketDataContext context;
  private final PrimitiveLongChannelRouter orderBooks;
  private final TradeChannelRouter trades;
  private final TradeSummaryContext tradeSummary = new TradeSummaryContext();
  private final InstrumentRegistry instruments;
  private final OrderBookStateRouter reconstructedBooks;
  private final StarbaseLongChannel referenceData = new StarbaseLongChannel();
  private final MarketDataPacketDispatcher dispatcher;
  private final AtomicLong receivedPacketCount = new AtomicLong();
  private final FeedSequenceTracker incrementalSequence = new FeedSequenceTracker();
  private final FeedSequenceTracker snapshotSequence = new FeedSequenceTracker();
  private final FeedDiagnostics incrementalDiagnostics = new FeedDiagnostics();
  private final FeedDiagnostics snapshotDiagnostics = new FeedDiagnostics();

  private volatile boolean transportRunning;
  private volatile MarketDataUdpReceiver incrementalReceiver;
  private volatile MarketDataUdpReceiver snapshotReceiver;
  private volatile Thread incrementalThread;
  private volatile Thread snapshotThread;
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
    this.context = Objects.requireNonNull(context, "context");
    orderBooks = new PrimitiveLongChannelRouter(instrumentCapacity);
    trades = new TradeChannelRouter(instrumentCapacity);
    instruments = new InstrumentRegistry(instrumentCapacity);
    reconstructedBooks = new OrderBookStateRouter(instrumentCapacity);
    dispatcher = new MarketDataPacketDispatcher(this::routeDecodedMessage);
  }

  public StarbaseMarketDataContext context() {
    return context;
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
    reconstructedBooks.configure(
        instrumentId, orderCapacity, levelCapacity, instruments, orderBooks);
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
          }
        }
      }
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
    return incremental != null
        && snapshot != null
        && incremental.isOpen()
        && snapshot.isOpen()
        && transportRunning;
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

  public FeedDiagnostics snapshotDiagnostics() {
    return snapshotDiagnostics;
  }

  @Override
  protected void onStart() {
    try {
      incrementalSequence.reset();
      snapshotSequence.reset();
      transportFailure = null;
      incrementalReceiver =
          openReceiver(
              context.incrementalGroup(), incrementalSequence, incrementalDiagnostics);
      snapshotReceiver =
          openReceiver(context.snapshotGroup(), snapshotSequence, snapshotDiagnostics);
      incrementalDiagnostics.onTransportOpen();
      snapshotDiagnostics.onTransportOpen();
      transportRunning = true;
      incrementalThread =
          startReceiverThread("incremental", incrementalReceiver, incrementalDiagnostics);
      snapshotThread =
          startReceiverThread("snapshot", snapshotReceiver, snapshotDiagnostics);
    } catch (IOException | RuntimeException failure) {
      closeReceiver(incrementalReceiver);
      closeReceiver(snapshotReceiver);
      incrementalReceiver = null;
      snapshotReceiver = null;
      throw new StarbaseException("failed to start Starbase market-data transport", failure);
    }
  }

  @Override
  protected void onClose() {
    transportRunning = false;
    closeReceiver(incrementalReceiver);
    closeReceiver(snapshotReceiver);
    joinReceiver(incrementalThread);
    joinReceiver(snapshotThread);
    incrementalDiagnostics.onTransportClosed();
    snapshotDiagnostics.onTransportClosed();
    incrementalReceiver = null;
    snapshotReceiver = null;
    incrementalThread = null;
    snapshotThread = null;
  }

  private MarketDataUdpReceiver openReceiver(
      java.net.InetSocketAddress endpoint,
      FeedSequenceTracker sequenceTracker,
      FeedDiagnostics diagnostics)
      throws IOException {
    return MarketDataUdpReceiver.open(
        endpoint,
        context.networkInterfaceName(),
        context.receiveBufferBytes(),
        context.ioPolicy(),
        (buffer, length) -> handlePacket(buffer, sequenceTracker, diagnostics));
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
      String feed, MarketDataUdpReceiver receiver, FeedDiagnostics diagnostics) {
    Thread thread =
        Thread.ofPlatform()
            .name(
                "starbase-md-"
                    + context.productGroup().name().toLowerCase()
                    + "-"
                    + context.gatewaySide().name().toLowerCase()
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
        diagnostics.onCorruptFrame();
        transportFailure = failure;
        transportRunning = false;
        closeReceiver(incrementalReceiver);
        closeReceiver(snapshotReceiver);
      }
    }
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
