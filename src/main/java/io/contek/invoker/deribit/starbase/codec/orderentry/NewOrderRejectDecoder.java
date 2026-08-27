package io.contek.invoker.deribit.starbase.codec.orderentry;

import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

/** Hardcoded decoder for order-entry schema-v15 NewOrderReject (template 202). */
public final class NewOrderRejectDecoder {

  public static final int TEMPLATE_ID = 202;
  public static final int BLOCK_LENGTH = 49;

  public static void validate(ByteBuffer buffer, int offset) {
    SessionCodecSupport.validateVariable(buffer, offset, TEMPLATE_ID, BLOCK_LENGTH);
    int reason = reason(buffer, offset);
    if (reason < 0 || reason > 30) {
      throw new StarbaseProtocolException("invalid NewOrderReject reason");
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

  public static int reason(ByteBuffer buffer, int offset) {
    return buffer.get(body(offset) + 48);
  }

  public static int detailsLength(ByteBuffer buffer, int offset) {
    return SessionCodecSupport.variableLength(buffer, offset, BLOCK_LENGTH);
  }

  public static int detailsByte(ByteBuffer buffer, int offset, int index) {
    return SessionCodecSupport.variableByte(buffer, offset, BLOCK_LENGTH, index);
  }

  private static int body(int offset) {
    return offset + TcpHeaderCodec.ENCODED_LENGTH;
  }

  private NewOrderRejectDecoder() {}
}
