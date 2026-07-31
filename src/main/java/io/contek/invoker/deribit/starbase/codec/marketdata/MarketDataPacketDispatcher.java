package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.MarketDataTemplateDispatch;
import io.contek.invoker.deribit.starbase.codec.common.UdpPacketHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;
import java.util.Objects;

/** Allocation-free validator and dispatcher for one complete Starbase UDP payload. */
public final class MarketDataPacketDispatcher {

  private final MarketDataMessageHandler handler;

  public MarketDataPacketDispatcher(MarketDataMessageHandler handler) {
    this.handler = Objects.requireNonNull(handler, "handler");
  }

  /**
   * Validates and dispatches one complete datagram whose end is the buffer limit.
   *
   * @return the number of messages dispatched
   */
  public int dispatch(ByteBuffer buffer, int packetOffset) {
    UdpPacketHeaderCodec.validate(buffer, packetOffset);
    int messageCount = UdpPacketHeaderCodec.messageCount(buffer, packetOffset);
    long firstSequence = UdpPacketHeaderCodec.sequenceNumber(buffer, packetOffset);
    if (firstSequence < 0) {
      throw new StarbaseProtocolException("negative UDP sequence number: " + firstSequence);
    }
    int messageOffset = packetOffset + UdpPacketHeaderCodec.ENCODED_LENGTH;
    for (int index = 0; index < messageCount; index++) {
      MarketDataMessageHeaderCodec.validate(buffer, messageOffset);
      int templateId = MarketDataTemplateDispatch.validateMessage(buffer, messageOffset);
      validateTemplate(buffer, messageOffset, templateId);
      long sequenceNumber;
      try {
        sequenceNumber = Math.addExact(firstSequence, index);
      } catch (ArithmeticException exception) {
        throw new StarbaseProtocolException("UDP message sequence overflow", exception);
      }
      handler.onMessage(buffer, messageOffset, templateId, sequenceNumber);
      messageOffset += MarketDataMessageHeaderCodec.messageLength(buffer, messageOffset);
    }
    if (messageOffset != buffer.limit()) {
      throw new StarbaseProtocolException(
          "UDP payload has trailing or uncounted bytes: decodedEnd="
              + messageOffset
              + ", limit="
              + buffer.limit());
    }
    return messageCount;
  }

  private static void validateTemplate(
      ByteBuffer buffer, int messageOffset, int templateId) {
    switch (templateId) {
      case InstrumentDefinitionDecoder.TEMPLATE_ID ->
          InstrumentDefinitionDecoder.validate(buffer, messageOffset);
      case IndexDefinitionDecoder.TEMPLATE_ID ->
          IndexDefinitionDecoder.validate(buffer, messageOffset);
      case InstrumentInfoDecoder.TEMPLATE_ID ->
          InstrumentInfoDecoder.validate(buffer, messageOffset);
      case InstrumentRefDecoder.TEMPLATE_ID ->
          InstrumentRefDecoder.validate(buffer, messageOffset);
      case InstrumentStatusUpdateDecoder.TEMPLATE_ID ->
          InstrumentStatusUpdateDecoder.validate(buffer, messageOffset);
      case BidPutDecoder.TEMPLATE_ID -> BidPutDecoder.validate(buffer, messageOffset);
      case AskPutDecoder.TEMPLATE_ID -> AskPutDecoder.validate(buffer, messageOffset);
      case BidQtyReducedDecoder.TEMPLATE_ID ->
          BidQtyReducedDecoder.validate(buffer, messageOffset);
      case AskQtyReducedDecoder.TEMPLATE_ID ->
          AskQtyReducedDecoder.validate(buffer, messageOffset);
      case BidDeleteDecoder.TEMPLATE_ID -> BidDeleteDecoder.validate(buffer, messageOffset);
      case AskDeleteDecoder.TEMPLATE_ID -> AskDeleteDecoder.validate(buffer, messageOffset);
      case TradeSummaryDecoder.TEMPLATE_ID ->
          TradeSummaryDecoder.validate(buffer, messageOffset);
      case TradeDecoder.TEMPLATE_ID -> TradeDecoder.validate(buffer, messageOffset);
      case SnapshotHeaderDecoder.TEMPLATE_ID ->
          SnapshotHeaderDecoder.validate(buffer, messageOffset);
      case SnapshotTrailerDecoder.TEMPLATE_ID ->
          SnapshotTrailerDecoder.validate(buffer, messageOffset);
      case EndOfCycleDecoder.TEMPLATE_ID ->
          EndOfCycleDecoder.validate(buffer, messageOffset);
      case RetransmitRejectDecoder.TEMPLATE_ID ->
          RetransmitRejectDecoder.validate(buffer, messageOffset);
      case RetransmitRequestEncoder.TEMPLATE_ID ->
          validateRetransmitRequest(buffer, messageOffset);
      default ->
          throw new StarbaseProtocolException(
              "unsupported state-changing market-data templateId: " + templateId);
    }
  }

  private static void validateRetransmitRequest(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.validateFixed(
        buffer,
        messageOffset,
        RetransmitRequestEncoder.TEMPLATE_ID,
        RetransmitRequestEncoder.BLOCK_LENGTH);
    int body = messageOffset + MarketDataMessageHeaderCodec.ENCODED_LENGTH;
    long beginSequence =
        buffer.getLong(body + RetransmitRequestEncoder.BEGIN_SEQUENCE_NUMBER_OFFSET);
    int count =
        Byte.toUnsignedInt(buffer.get(body + RetransmitRequestEncoder.MESSAGE_COUNT_OFFSET));
    if (beginSequence < 0 || count < 1) {
      throw new StarbaseProtocolException("invalid RetransmitRequest body");
    }
  }
}
