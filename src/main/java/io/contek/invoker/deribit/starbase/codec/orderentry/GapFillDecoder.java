package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

public final class GapFillDecoder {

  public static final int TEMPLATE_ID = 21;
  public static final int BODY_LENGTH = 8;

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(buffer, offset, TEMPLATE_ID, BODY_LENGTH);
    if (newSequenceNumber(buffer, offset) < 1) {
      throw new StarbaseProtocolException("invalid GapFill newSequenceNumber");
    }
  }

  public static long newSequenceNumber(ByteBuffer buffer, int offset) {
    return buffer.getLong(offset + SessionCodecSupport.BODY_OFFSET);
  }

  private GapFillDecoder() {}
}
