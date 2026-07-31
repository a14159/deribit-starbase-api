package io.contek.invoker.deribit.starbase.codec.marketdata;

import io.contek.invoker.deribit.starbase.codec.common.MarketDataMessageHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.common.MarketDataTemplateDispatch;
import io.contek.invoker.deribit.starbase.codec.common.WirePrimitives;
import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import java.nio.ByteBuffer;

final class MarketDataDecoderSupport {

  static final int BODY_OFFSET = MarketDataMessageHeaderCodec.ENCODED_LENGTH;

  static void validateFixed(
      ByteBuffer buffer, int messageOffset, int templateId, int blockLength) {
    int actualTemplate = MarketDataTemplateDispatch.validateMessage(buffer, messageOffset);
    if (actualTemplate != templateId) {
      throw new StarbaseProtocolException(
          "expected market-data template " + templateId + " but received " + actualTemplate);
    }
    validateEncodedLength(buffer, messageOffset, templateId, BODY_OFFSET + blockLength);
  }

  static void validateEncodedLength(
      ByteBuffer buffer, int messageOffset, int templateId, int unpaddedLength) {
    int actualLength = MarketDataMessageHeaderCodec.messageLength(buffer, messageOffset);
    int alignedLength = WirePrimitives.align8(unpaddedLength);
    if (actualLength != unpaddedLength && actualLength != alignedLength) {
      throw new StarbaseProtocolException(
          "invalid template "
              + templateId
              + " messageLength: "
              + actualLength
              + ", expected "
              + unpaddedLength
              + " or aligned "
              + alignedLength);
    }
    for (int index = unpaddedLength; index < actualLength; index++) {
      if (buffer.get(messageOffset + index) != 0) {
        throw new StarbaseProtocolException(
            "non-zero template " + templateId + " padding at offset " + index);
      }
    }
  }

  static void requireBody(ByteBuffer buffer, int messageOffset, int blockLength) {
    WirePrimitives.requireLittleEndian(buffer);
    WirePrimitives.requireBounds(buffer, messageOffset, BODY_OFFSET + blockLength);
  }

  static int fixedAsciiLength(
      ByteBuffer buffer, int messageOffset, int fieldOffset, int fieldLength) {
    requireBody(buffer, messageOffset, fieldOffset + fieldLength);
    int length = 0;
    while (length < fieldLength && buffer.get(messageOffset + BODY_OFFSET + fieldOffset + length) != 0) {
      length++;
    }
    return length;
  }

  static int fixedAsciiByte(
      ByteBuffer buffer, int messageOffset, int fieldOffset, int fieldLength, int index) {
    if (index < 0 || index >= fieldLength) {
      throw new IndexOutOfBoundsException("fixed ASCII index: " + index);
    }
    requireBody(buffer, messageOffset, fieldOffset + fieldLength);
    return Byte.toUnsignedInt(buffer.get(messageOffset + BODY_OFFSET + fieldOffset + index));
  }

  private MarketDataDecoderSupport() {}
}
