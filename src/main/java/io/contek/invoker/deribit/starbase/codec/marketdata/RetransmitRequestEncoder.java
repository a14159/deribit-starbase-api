package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.WirePrimitives;
import io.contek.invoker.deribit.starbase.protocol.ProtocolSchemas;
import java.nio.ByteBuffer;

/** Encoder for retransmit-request market-data template 200. */
public final class RetransmitRequestEncoder {

  public static final int TEMPLATE_ID = 200;
  public static final int BLOCK_LENGTH = 9;
  public static final int ENCODED_LENGTH =
      MarketDataMessageHeaderCodec.ENCODED_LENGTH + BLOCK_LENGTH;
  public static final int BEGIN_SEQUENCE_NUMBER_OFFSET = 0;
  public static final int MESSAGE_COUNT_OFFSET = 8;
  public static final int MAX_MESSAGE_COUNT = 255;

  public static void encode(
      ByteBuffer buffer,
      int messageOffset,
      long beginSequenceNumber,
      int messageCount,
      long transactTimeNanos) {
    if (beginSequenceNumber < 0) {
      throw new IllegalArgumentException(
          "beginSequenceNumber must be nonnegative: " + beginSequenceNumber);
    }
    if (messageCount < 1 || messageCount > MAX_MESSAGE_COUNT) {
      throw new IllegalArgumentException("messageCount out of range: " + messageCount);
    }
    WirePrimitives.requireLittleEndian(buffer);
    WirePrimitives.requireBounds(buffer, messageOffset, ENCODED_LENGTH);
    buffer.putShort(
        messageOffset + MarketDataMessageHeaderCodec.MESSAGE_LENGTH_OFFSET,
        (short) ENCODED_LENGTH);
    buffer.putShort(
        messageOffset + MarketDataMessageHeaderCodec.TEMPLATE_ID_OFFSET,
        (short) TEMPLATE_ID);
    buffer.putShort(
        messageOffset + MarketDataMessageHeaderCodec.VERSION_OFFSET,
        (short) ProtocolSchemas.MARKET_DATA.version());
    buffer.putShort(messageOffset + MarketDataMessageHeaderCodec.FLAGS_OFFSET, (short) 0);
    buffer.putLong(
        messageOffset + MarketDataMessageHeaderCodec.TRANSACT_TIME_NANOS_OFFSET,
        transactTimeNanos);
    int body = messageOffset + MarketDataMessageHeaderCodec.ENCODED_LENGTH;
    buffer.putLong(body + BEGIN_SEQUENCE_NUMBER_OFFSET, beginSequenceNumber);
    buffer.put(body + MESSAGE_COUNT_OFFSET, (byte) messageCount);
  }

  private RetransmitRequestEncoder() {}
}
