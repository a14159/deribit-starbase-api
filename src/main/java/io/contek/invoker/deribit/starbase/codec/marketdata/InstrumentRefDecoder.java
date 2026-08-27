package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.codec.common.Price9Codec;
import java.nio.ByteBuffer;

/** Decoder for market-data template 15. */
public final class InstrumentRefDecoder {

  public static final int TEMPLATE_ID = 15;
  public static final int BLOCK_LENGTH = 56;
  public static final int INSTRUMENT_ID_OFFSET = 0;
  public static final int CURRENT_FUNDING_OFFSET = 8;
  public static final int FUNDING_8H_OFFSET = 16;
  public static final int ESTIMATED_DELIVERY_PRICE_OFFSET = 24;
  public static final int DELIVERY_PRICE_OFFSET = 32;
  public static final int SETTLEMENT_PRICE_OFFSET = 40;
  public static final int OPEN_INTEREST_OFFSET = 48;

  public static void validate(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.validateFixed(buffer, messageOffset, TEMPLATE_ID, BLOCK_LENGTH);
  }

  public static long instrumentId(ByteBuffer buffer, int messageOffset) {
    require(buffer, messageOffset);
    return buffer.getLong(body(messageOffset) + INSTRUMENT_ID_OFFSET);
  }

  public static double currentFunding(ByteBuffer buffer, int messageOffset) {
    require(buffer, messageOffset);
    return buffer.getDouble(body(messageOffset) + CURRENT_FUNDING_OFFSET);
  }

  public static boolean isCurrentFundingNull(ByteBuffer buffer, int messageOffset) {
    return Double.isNaN(currentFunding(buffer, messageOffset));
  }

  public static double funding8h(ByteBuffer buffer, int messageOffset) {
    require(buffer, messageOffset);
    return buffer.getDouble(body(messageOffset) + FUNDING_8H_OFFSET);
  }

  public static boolean isFunding8hNull(ByteBuffer buffer, int messageOffset) {
    return Double.isNaN(funding8h(buffer, messageOffset));
  }

  public static long estimatedDeliveryPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return price(buffer, messageOffset, ESTIMATED_DELIVERY_PRICE_OFFSET);
  }

  public static boolean isEstimatedDeliveryPriceNull(ByteBuffer buffer, int messageOffset) {
    return estimatedDeliveryPriceMantissa(buffer, messageOffset) == Price9Codec.NULL_MANTISSA;
  }

  public static long deliveryPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return price(buffer, messageOffset, DELIVERY_PRICE_OFFSET);
  }

  public static boolean isDeliveryPriceNull(ByteBuffer buffer, int messageOffset) {
    return deliveryPriceMantissa(buffer, messageOffset) == Price9Codec.NULL_MANTISSA;
  }

  public static long settlementPriceMantissa(ByteBuffer buffer, int messageOffset) {
    return price(buffer, messageOffset, SETTLEMENT_PRICE_OFFSET);
  }

  public static boolean isSettlementPriceNull(ByteBuffer buffer, int messageOffset) {
    return settlementPriceMantissa(buffer, messageOffset) == Price9Codec.NULL_MANTISSA;
  }

  public static double openInterest(ByteBuffer buffer, int messageOffset) {
    require(buffer, messageOffset);
    return buffer.getDouble(body(messageOffset) + OPEN_INTEREST_OFFSET);
  }

  public static boolean isOpenInterestNull(ByteBuffer buffer, int messageOffset) {
    return Double.isNaN(openInterest(buffer, messageOffset));
  }

  private static long price(ByteBuffer buffer, int messageOffset, int fieldOffset) {
    require(buffer, messageOffset);
    return buffer.getLong(body(messageOffset) + fieldOffset);
  }

  private static int body(int messageOffset) {
    return messageOffset + MarketDataDecoderSupport.BODY_OFFSET;
  }

  private static void require(ByteBuffer buffer, int messageOffset) {
    MarketDataDecoderSupport.requireBody(buffer, messageOffset, BLOCK_LENGTH);
  }

  private InstrumentRefDecoder() {}
}
