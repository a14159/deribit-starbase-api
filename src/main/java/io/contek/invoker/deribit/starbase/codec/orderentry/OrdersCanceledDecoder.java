package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.codec.common.Decimal72Codec;
import io.contek.invoker.deribit.starbase.codec.common.OrderEntryTemplateDispatch;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.WirePrimitives;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry schema-v11 OrdersCanceled (template 310). */
public final class OrdersCanceledDecoder {

  public static final int TEMPLATE_ID = 310;
  public static final int BLOCK_LENGTH = 17;
  public static final int GROUP_DIMENSIONS_LENGTH = 4;
  public static final int ORDER_BLOCK_LENGTH = 35;
  public static final int KNOWN_MULTI_PART_FLAGS = 0x01;
  public static final int KNOWN_CANCEL_FLAGS = 0x01;

  public static void validate(ByteBuffer buffer, int offset) {
    OrderEntryTemplateDispatch.validateFrame(buffer, offset);
    requireTemplate(buffer, offset);
    WirePrimitives.requireBounds(buffer, body(offset), BLOCK_LENGTH);
    if ((multiPartFlags(buffer, offset) & ~KNOWN_MULTI_PART_FLAGS) != 0) {
      throw new StarbaseProtocolException("invalid OrdersCanceled multipart flags");
    }
    int dimensions = ordersDimensions(offset);
    WirePrimitives.requireBounds(buffer, dimensions, GROUP_DIMENSIONS_LENGTH);
    int blockLength = Short.toUnsignedInt(buffer.getShort(dimensions));
    int count = Short.toUnsignedInt(buffer.getShort(dimensions + 2));
    if (blockLength != ORDER_BLOCK_LENGTH || count > 2000) {
      throw new StarbaseProtocolException("invalid OrdersCanceled group dimensions");
    }
    long expectedEnd =
        (long) dimensions + GROUP_DIMENSIONS_LENGTH + (long) count * ORDER_BLOCK_LENGTH;
    if (expectedEnd - offset != TcpHeaderCodec.messageLength(buffer, offset)) {
      throw new StarbaseProtocolException("OrdersCanceled group length mismatch");
    }
    for (int index = 0; index < count; index++) {
      int entry = dimensions + GROUP_DIMENSIONS_LENGTH + index * ORDER_BLOCK_LENGTH;
      requireRequiredLong(buffer.getLong(entry), "clientOrderId");
      requireRequiredLong(buffer.getLong(entry + 8), "orderId");
      requireRequiredLong(buffer.getLong(entry + 16), "instrumentId");
      requireDecimal(buffer, entry + 24, "totalFilled");
      int reason = buffer.get(entry + 33);
      int flags = Byte.toUnsignedInt(buffer.get(entry + 34));
      if (reason < 0 || reason > 17) {
        throw new StarbaseProtocolException("invalid OrdersCanceled cancel reason");
      }
      if ((flags & ~KNOWN_CANCEL_FLAGS) != 0) {
        throw new StarbaseProtocolException("invalid OrdersCanceled cancel flags");
      }
    }
  }

  public static long timestampNanos(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset));
  }

  public static long execId(ByteBuffer buffer, int offset) {
    return buffer.getLong(body(offset) + 8);
  }

  public static int multiPartFlags(ByteBuffer buffer, int offset) {
    return Byte.toUnsignedInt(buffer.get(body(offset) + 16));
  }

  public static int orderCount(ByteBuffer buffer, int offset) {
    return Short.toUnsignedInt(buffer.getShort(ordersDimensions(offset) + 2));
  }

  public static long clientOrderId(ByteBuffer buffer, int offset, int index) {
    return buffer.getLong(orderEntry(buffer, offset, index));
  }

  public static long orderId(ByteBuffer buffer, int offset, int index) {
    return buffer.getLong(orderEntry(buffer, offset, index) + 8);
  }

  public static long instrumentId(ByteBuffer buffer, int offset, int index) {
    return buffer.getLong(orderEntry(buffer, offset, index) + 16);
  }

  public static long totalFilledMantissa(ByteBuffer buffer, int offset, int index) {
    return Decimal72Codec.mantissa(buffer, orderEntry(buffer, offset, index) + 24);
  }

  public static int totalFilledExponent(ByteBuffer buffer, int offset, int index) {
    return Decimal72Codec.exponent(buffer, orderEntry(buffer, offset, index) + 24);
  }

  public static int cancelReason(ByteBuffer buffer, int offset, int index) {
    return buffer.get(orderEntry(buffer, offset, index) + 33);
  }

  public static int flags(ByteBuffer buffer, int offset, int index) {
    return Byte.toUnsignedInt(buffer.get(orderEntry(buffer, offset, index) + 34));
  }

  private static int orderEntry(ByteBuffer buffer, int offset, int index) {
    int count = orderCount(buffer, offset);
    if (index < 0 || index >= count) {
      throw new IndexOutOfBoundsException("order index " + index + ", count " + count);
    }
    return ordersDimensions(offset) + GROUP_DIMENSIONS_LENGTH + index * ORDER_BLOCK_LENGTH;
  }

  private static int ordersDimensions(int offset) {
    return body(offset) + BLOCK_LENGTH;
  }

  private static int body(int offset) {
    return offset + SessionCodecSupport.BODY_OFFSET;
  }

  private static void requireTemplate(ByteBuffer buffer, int offset) {
    int actual = TcpHeaderCodec.messageTypeId(buffer, offset);
    if (actual != TEMPLATE_ID) {
      throw new StarbaseProtocolException(
          "expected OrdersCanceled template " + TEMPLATE_ID + " but received " + actual);
    }
  }

  private static void requireRequiredLong(long value, String field) {
    if (value == Long.MIN_VALUE) {
      throw new StarbaseProtocolException("null OrdersCanceled " + field);
    }
  }

  private static void requireDecimal(ByteBuffer buffer, int offset, String field) {
    long mantissa = Decimal72Codec.mantissa(buffer, offset);
    int exponent = Decimal72Codec.exponent(buffer, offset);
    if (mantissa == Decimal72Codec.NULL_MANTISSA
        || exponent == Decimal72Codec.NULL_EXPONENT
        || mantissa < 0) {
      throw new StarbaseProtocolException("invalid OrdersCanceled " + field);
    }
  }

  private OrdersCanceledDecoder() {}
}
