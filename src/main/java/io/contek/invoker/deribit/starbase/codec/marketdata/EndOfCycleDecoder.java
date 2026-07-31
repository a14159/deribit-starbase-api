package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Decoder for end-of-snapshot-cycle market-data template 119. */
public final class EndOfCycleDecoder {

  public static final int TEMPLATE_ID = 119;
  public static final int BLOCK_LENGTH = Integer.BYTES;
  public static final int ACTIVE_INSTRUMENT_COUNT_OFFSET = 0;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.validateFixed(buffer, messageOffset, TEMPLATE_ID, BLOCK_LENGTH);
    if (activeInstrumentCount(buffer, messageOffset) < 0) {
      throw new StarbaseProtocolException("negative EndOfCycle activeInstrumentCount");
    }
  }

  public static int activeInstrumentCount(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
    return buffer.getInt(
        messageOffset
            + MarketDataDecoderSupport.BODY_OFFSET
            + ACTIVE_INSTRUMENT_COUNT_OFFSET);
  }

  private EndOfCycleDecoder() {}
}
