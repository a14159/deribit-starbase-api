package io.contek.invoker.deribit.starbase.codec.marketdata;

import java.nio.ByteBuffer;

/** Decoder for ask-delete market-data template 25. */
public final class AskDeleteDecoder {

  public static final int TEMPLATE_ID = 25;
  public static final int BLOCK_LENGTH = BookMutationDecoderSupport.DELETE_BLOCK_LENGTH;
  public static final int SIDE = -1;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    BookMutationDecoderSupport.validateDelete(buffer, messageOffset, TEMPLATE_ID);
  }

  public static long orderId(ByteBuffer buffer, int messageOffset) {
    return BookMutationDecoderSupport.orderId(buffer, messageOffset, BLOCK_LENGTH);
  }

  public static long instrumentId(ByteBuffer buffer, int messageOffset) {
    return BookMutationDecoderSupport.instrumentId(buffer, messageOffset, BLOCK_LENGTH);
  }

  private AskDeleteDecoder() {}
}
