package io.contek.invoker.deribit.starbase.codec.marketdata;

import java.nio.ByteBuffer;

/** Decoder for bid-delete market-data template 24. */
public final class BidDeleteDecoder {

  public static final int TEMPLATE_ID = 24;
  public static final int BLOCK_LENGTH = BookMutationDecoderSupport.DELETE_BLOCK_LENGTH;
  public static final int SIDE = 1;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    BookMutationDecoderSupport.validateDelete(buffer, messageOffset, TEMPLATE_ID);
  }

  public static long orderId(ByteBuffer buffer, int messageOffset) {
    return BookMutationDecoderSupport.orderId(buffer, messageOffset, BLOCK_LENGTH);
  }

  public static long instrumentId(ByteBuffer buffer, int messageOffset) {
    return BookMutationDecoderSupport.instrumentId(buffer, messageOffset, BLOCK_LENGTH);
  }

  private BidDeleteDecoder() {}
}
