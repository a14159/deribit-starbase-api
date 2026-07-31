package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Decoder for retransmit-reject market-data template 202. */
public final class RetransmitRejectDecoder {

  public static final int TEMPLATE_ID = 202;
  public static final int BLOCK_LENGTH = 49;
  public static final int RETRY_DELAY_NANOS_OFFSET = 0;
  public static final int DETAILS_OFFSET = 8;
  public static final int DETAILS_LENGTH = 40;
  public static final int REASON_OFFSET = 48;

  public static final int REASON_SEQUENCE_TOO_LOW = 1;
  public static final int REASON_SEQUENCE_TOO_HIGH = 2;
  public static final int REASON_RATE_LIMIT_EXCEEDED = 3;
  public static final int REASON_OTHER_ERROR = 4;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.validateFixed(buffer, messageOffset, TEMPLATE_ID, BLOCK_LENGTH);
    if (retryDelayNanos(buffer, messageOffset) < 0) {
      throw new StarbaseProtocolException("negative RetransmitReject retryDelayNanos");
    }
    int reason = reason(buffer, messageOffset);
    if (reason < REASON_SEQUENCE_TOO_LOW || reason > REASON_OTHER_ERROR) {
      throw new StarbaseProtocolException("unknown RetransmitRejectReason: " + reason);
    }
  }

  public static long retryDelayNanos(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
    return buffer.getLong(
        messageOffset
            + MarketDataDecoderSupport.BODY_OFFSET
            + RETRY_DELAY_NANOS_OFFSET);
  }

  public static int detailsLength(ByteBuffer buffer, int messageOffset) {
    return MarketDataDecoderSupport.fixedAsciiLength(
        buffer, messageOffset, DETAILS_OFFSET, DETAILS_LENGTH);
  }

  public static int detailsByte(ByteBuffer buffer, int messageOffset, int index) {
    return MarketDataDecoderSupport.fixedAsciiByte(
        buffer, messageOffset, DETAILS_OFFSET, DETAILS_LENGTH, index);
  }

  public static int reason(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
    return buffer.get(
        messageOffset + MarketDataDecoderSupport.BODY_OFFSET + REASON_OFFSET);
  }

  private RetransmitRejectDecoder() {}
}
