package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.OrderEntryTemplateDispatch;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry CancelOrderResponse (template 220), through schema v16. */
public final class CancelOrderResponseDecoder {

  public static final int TEMPLATE_ID = 220;
  public static final int LEGACY_BODY_LENGTH = 56;
  public static final int BODY_LENGTH = 74;
  public static final int AUTHORITATIVE_QUANTITIES_VERSION = 16;

  public static void validate(ByteBuffer buffer, int offset) {
    int templateId = OrderEntryTemplateDispatch.validateFrame(buffer, offset);
    if (templateId != TEMPLATE_ID) {
      throw new StarbaseProtocolException(
          "expected CancelOrderResponse template " + TEMPLATE_ID + " but received " + templateId);
    }
    int version = TcpHeaderCodec.version(buffer, offset);
    int bodyLength =
        version >= AUTHORITATIVE_QUANTITIES_VERSION ? BODY_LENGTH : LEGACY_BODY_LENGTH;
    int actualLength = TcpHeaderCodec.messageLength(buffer, offset);
    int expectedLength = SessionCodecSupport.BODY_OFFSET + bodyLength;
    if (actualLength != expectedLength) {
      throw new StarbaseProtocolException(
          "CancelOrderResponse length mismatch: expected="
              + expectedLength
              + ", actual="
              + actualLength);
    }
    if (version >= AUTHORITATIVE_QUANTITIES_VERSION) {
      requireRequiredDecimal(buffer, body(offset) + 56, true, "quantity");
      requireRequiredDecimal(buffer, body(offset) + 65, false, "totalFilled");
    }
  }

  public static long timestampNanos(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset));
  }

  public static long execId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 8);
  }

  public static long clientOrderId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 16);
  }

  public static long correlationId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 24);
  }

  public static long orderId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 32);
  }

  public static long instrumentId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 40);
  }

  public static long receiveTimeNanos(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 48);
  }

  public static boolean hasAuthoritativeQuantities(ByteBuffer buffer, int offset) {
    return TcpHeaderCodec.version(buffer, offset) >= AUTHORITATIVE_QUANTITIES_VERSION;
  }

  public static long quantityMantissa(ByteBuffer buffer, int offset) {
    requireAuthoritativeQuantities(buffer, offset);
    return Decimal72Codec.mantissa(buffer, body(offset) + 56);
  }

  public static int quantityExponent(ByteBuffer buffer, int offset) {
    requireAuthoritativeQuantities(buffer, offset);
    return Decimal72Codec.exponent(buffer, body(offset) + 56);
  }

  public static long totalFilledMantissa(ByteBuffer buffer, int offset) {
    requireAuthoritativeQuantities(buffer, offset);
    return Decimal72Codec.mantissa(buffer, body(offset) + 65);
  }

  public static int totalFilledExponent(ByteBuffer buffer, int offset) {
    requireAuthoritativeQuantities(buffer, offset);
    return Decimal72Codec.exponent(buffer, body(offset) + 65);
  }

  private static void requireRequiredDecimal(
      ByteBuffer buffer, int offset, boolean positive, String field) {
    long mantissa = Decimal72Codec.mantissa(buffer, offset);
    int exponent = Decimal72Codec.exponent(buffer, offset);
    if (mantissa == Decimal72Codec.NULL_MANTISSA
        || exponent == Decimal72Codec.NULL_EXPONENT
        || positive && mantissa <= 0
        || !positive && mantissa < 0) {
      throw new StarbaseProtocolException("invalid CancelOrderResponse " + field);
    }
  }

  private static void requireAuthoritativeQuantities(ByteBuffer buffer, int offset) {
    if (!hasAuthoritativeQuantities(buffer, offset)) {
      throw new StarbaseProtocolException(
          "CancelOrderResponse quantities are absent before schema v16");
    }
  }

  private static int body(int offset) {
    return offset + SessionCodecSupport.BODY_OFFSET;
  }

  private CancelOrderResponseDecoder() {}
}
