package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import java.nio.ByteBuffer;

/** Decoder for snapshot-trailer market-data template 101. */
public final class SnapshotTrailerDecoder {

  public static final int TEMPLATE_ID = 101;
  public static final int BLOCK_LENGTH = SnapshotDecoderSupport.BLOCK_LENGTH;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    SnapshotDecoderSupport.validate(
        buffer,
        messageOffset,
        TEMPLATE_ID,
        MarketDataMessageHeaderCodec.FLAG_END_OF_TRANSACTION);
  }

  public static long instrumentId(ByteBuffer buffer, int messageOffset) {
    return SnapshotDecoderSupport.instrumentId(buffer, messageOffset);
  }

  public static long incrementalTimestampNanos(ByteBuffer buffer, int messageOffset) {
    return SnapshotDecoderSupport.incrementalTimestampNanos(buffer, messageOffset);
  }

  public static long incrementalSequenceNumber(ByteBuffer buffer, int messageOffset) {
    return SnapshotDecoderSupport.incrementalSequenceNumber(buffer, messageOffset);
  }

  public static boolean isStartOfTransaction(ByteBuffer buffer, int messageOffset) {
    return MarketDataMessageHeaderCodec.isStartOfTransaction(buffer, messageOffset);
  }

  public static boolean isEndOfTransaction(ByteBuffer buffer, int messageOffset) {
    return MarketDataMessageHeaderCodec.isEndOfTransaction(buffer, messageOffset);
  }

  private SnapshotTrailerDecoder() {}
}
