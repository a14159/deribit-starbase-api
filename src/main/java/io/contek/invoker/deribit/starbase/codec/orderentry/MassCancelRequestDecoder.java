package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry schema-v11 MassCancelRequest (template 140). */
public final class MassCancelRequestDecoder {

  public static final int TEMPLATE_ID = 140;
  public static final int BODY_LENGTH = 26;

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateFixed(buffer, offset, TEMPLATE_ID, BODY_LENGTH);
    try {
      MassCancelRequestEncoder.validateScope(
          currencyPairId(buffer, offset),
          instrumentId(buffer, offset),
          productType(buffer, offset),
          side(buffer, offset));
    } catch (IllegalArgumentException invalid) {
      throw new StarbaseProtocolException("invalid MassCancelRequest scope", invalid);
    }
  }

  public static long correlationId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset));
  }

  public static long currencyPairId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 8);
  }

  public static boolean isCurrencyPairIdNull(ByteBuffer buffer, int offset) {
    return currencyPairId(buffer, offset) == MassCancelRequestEncoder.NULL_ID;
  }

  public static long instrumentId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 16);
  }

  public static boolean isInstrumentIdNull(ByteBuffer buffer, int offset) {
    return instrumentId(buffer, offset) == MassCancelRequestEncoder.NULL_ID;
  }

  public static int productType(ByteBuffer buffer, int offset) {
    return buffer.get(body(offset) + 24);
  }

  public static int side(ByteBuffer buffer, int offset) {
    return buffer.get(body(offset) + 25);
  }

  private static int body(int offset) {
    return offset + SessionCodecSupport.BODY_OFFSET;
  }

  private MassCancelRequestDecoder() {}
}
