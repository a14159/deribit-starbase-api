package io.contek.invoker.deribit.starbase.codec.marketdata;

import java.nio.ByteBuffer;

/** Decoder for bid-quantity-reduced market-data template 22. */
public final class BidQtyReducedDecoder {

  public static final int TEMPLATE_ID = 22;
  public static final int BLOCK_LENGTH = BookMutationDecoderSupport.REDUCED_BLOCK_LENGTH;
  public static final int SIDE = 1;

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

  private BidQtyReducedDecoder() {}
}
