package io.contek.invoker.deribit.starbase.codec.marketdata;

import java.nio.ByteBuffer;

/** Decoder for ask-quantity-reduced market-data template 23. */
public final class AskQtyReducedDecoder {

  public static final int TEMPLATE_ID = 23;
  public static final int BLOCK_LENGTH = BookMutationDecoderSupport.REDUCED_BLOCK_LENGTH;
  public static final int SIDE = -1;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    BookMutationDecoderSupport.validateReduced(buffer, messageOffset, TEMPLATE_ID);
  }

  public static long orderId(ByteBuffer buffer, int messageOffset) {
    return BookMutationDecoderSupport.orderId(buffer, messageOffset, BLOCK_LENGTH);
  }

  public static long instrumentId(ByteBuffer buffer, int messageOffset) {
    return BookMutationDecoderSupport.instrumentId(buffer, messageOffset, BLOCK_LENGTH);
  }

  public static long quantityMantissa(ByteBuffer buffer, int messageOffset) {
    return BookMutationDecoderSupport.quantityMantissa(buffer, messageOffset);
  }

  private AskQtyReducedDecoder() {}
}
