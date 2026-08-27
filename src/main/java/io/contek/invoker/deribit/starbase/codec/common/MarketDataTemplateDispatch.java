package io.contek.invoker.deribit.starbase.codec.common;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import io.contek.invoker.deribit.starbase.protocol.ProtocolSchemas;
import java.nio.ByteBuffer;

/** Allocation-free validation and dispatch keys for pinned market-data schema 2102. */
public final class MarketDataTemplateDispatch {

  public static final int MINIMUM_COMPATIBLE_VERSION = 0;

  public static int validateMessage(ByteBuffer buffer, int messageOffset) {
    MarketDataMessageHeaderCodec.validate(buffer, messageOffset);
    int version = MarketDataMessageHeaderCodec.version(buffer, messageOffset);
    if (version < MINIMUM_COMPATIBLE_VERSION
        || version > ProtocolSchemas.MARKET_DATA.version()) {
      throw new StarbaseProtocolException("unsupported market-data schema version: " + version);
    }
    return requireKnown(MarketDataMessageHeaderCodec.templateId(buffer, messageOffset));
  }

  public static int requireKnown(int templateId) {
    return switch (templateId) {
      case 10, 11, 12, 14, 15, 16, 20, 21, 22, 23, 24, 25, 30, 31, 33,
          100, 101, 119, 200, 202 -> templateId;
      default -> throw new StarbaseProtocolException(
          "unknown market-data templateId: " + templateId);
    };
  }

  /**
   * Conservatively classifies feed-state mutations. Unknown IDs fail closed rather than being
   * treated as harmless.
   */
  public static boolean isStateChanging(int templateId) {
    requireKnown(templateId);
    return switch (templateId) {
      case 119, 200, 202 -> false;
      default -> true;
    };
  }

  private MarketDataTemplateDispatch() {}
}
