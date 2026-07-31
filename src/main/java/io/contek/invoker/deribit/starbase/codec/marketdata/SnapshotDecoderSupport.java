package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

final class SnapshotDecoderSupport {

  static final int BLOCK_LENGTH = 24;
  static final int INSTRUMENT_ID_OFFSET = 0;
  static final int INCREMENTAL_TIMESTAMP_OFFSET = 8;
  static final int INCREMENTAL_SEQUENCE_OFFSET = 16;

  static void validate(
      ByteBuffer buffer, int messageOffset, int templateId, int expectedFlags) {
    MarketDataDecoderSupport.validateFixed(buffer, messageOffset, templateId, BLOCK_LENGTH);
    int flags = MarketDataMessageHeaderCodec.flags(buffer, messageOffset);
    if (flags != expectedFlags) {
      throw new StarbaseProtocolException(
          "invalid snapshot template "
              + templateId
              + " transaction flags: "
              + flags
              + ", expected "
              + expectedFlags);
    }
    requireNonNull(instrumentId(buffer, messageOffset), "instrumentId");
    requireNonNull(
        incrementalTimestampNanos(buffer, messageOffset), "incrementalTimestamp");
    requireNonNull(
        incrementalSequenceNumber(buffer, messageOffset), "incrementalSequenceNumber");
  }

  static long instrumentId(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, INSTRUMENT_ID_OFFSET);
  }

  static long incrementalTimestampNanos(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, INCREMENTAL_TIMESTAMP_OFFSET);
  }

  static long incrementalSequenceNumber(ByteBuffer buffer, int messageOffset) {
    return value(buffer, messageOffset, INCREMENTAL_SEQUENCE_OFFSET);
  }

  private static long value(ByteBuffer buffer, int messageOffset, int fieldOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
    return buffer.getLong(
        messageOffset + MarketDataDecoderSupport.BODY_OFFSET + fieldOffset);
  }

  private static void requireNonNull(long value, String field) {
    if (value == Long.MIN_VALUE) {
      throw new StarbaseProtocolException("null required snapshot " + field);
    }
  }

  private SnapshotDecoderSupport() {}
}
