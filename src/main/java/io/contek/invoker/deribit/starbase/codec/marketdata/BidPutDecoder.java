package io.contek.invoker.deribit.starbase.codec.marketdata;

import java.nio.ByteBuffer;

/** Decoder for bid-put market-data template 20. */
public final class BidPutDecoder {

  public static final int TEMPLATE_ID = 20;
  public static final int BLOCK_LENGTH = BookPutDecoderSupport.BLOCK_LENGTH;
  public static final int SIDE = 1;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    BookPutDecoderSupport.validate(buffer, messageOffset, TEMPLATE_ID);
  }

  public static long orderId(ByteBuffer buffer, int messageOffset) {
    return BookPutDecoderSupport.orderId(buffer, messageOffset);
  }

  public static long instrumentId(ByteBuffer buffer, int messageOffset) {
    return BookPutDecoderSupport.instrumentId(buffer, messageOffset);
  }

  public static long quantityMantissa(ByteBuffer buffer, int messageOffset) {
    return BookPutDecoderSupport.quantityMantissa(buffer, messageOffset);
  }

  public static long priceMantissa(ByteBuffer buffer, int messageOffset) {
    return BookPutDecoderSupport.priceMantissa(buffer, messageOffset);
  }

  public static long sortOrderId(ByteBuffer buffer, int messageOffset) {
    return BookPutDecoderSupport.sortOrderId(buffer, messageOffset);
  }

  private BidPutDecoder() {}
}
