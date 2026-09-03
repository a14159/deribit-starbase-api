package io.contek.invoker.deribit.starbase.codec.common;

import io.contek.invoker.deribit.starbase.common.StarbaseProtocolException;
import io.contek.invoker.deribit.starbase.protocol.ProtocolSchemas;
import java.nio.ByteBuffer;

/** Allocation-free validation and dispatch keys for pinned order-entry schema 2101. */
public final class OrderEntryTemplateDispatch {

  public static int validateFrame(ByteBuffer buffer, int headerOffset) {
    TcpHeaderCodec.validateFrame(buffer, headerOffset);
    int templateId = requireKnown(TcpHeaderCodec.messageTypeId(buffer, headerOffset));
    int version = TcpHeaderCodec.version(buffer, headerOffset);
    if (version < minimumCompatibleVersion(templateId)
        || version > ProtocolSchemas.ORDER_ENTRY.version()) {
      throw new StarbaseProtocolException(
          "unsupported order-entry schema version " + version + " for template " + templateId);
    }
    return templateId;
  }

  /**
   * Returns the first schema version compatible with the current hardcoded message layout and its
   * referenced enum/set definitions. Deribit stamps each server message with its own last-change
   * version, capped by the negotiated session version, rather than stamping every message with the
   * session ceiling.
   */
  public static int minimumCompatibleVersion(int templateId) {
    requireKnown(templateId);
    return switch (templateId) {
      case 1 -> 13;
      case 2 -> 12;
      case 30, 202, 212, 230, 232 -> 14;
      case 100, 130, 155, 156, 280, 281, 282, 283 -> 10;
      case 125 -> 3;
      case 200, 210, 310, 314 -> 5;
      case 222 -> 9;
      case 312 -> 8;
      default -> 0;
    };
  }

  public static int requireKnown(int templateId) {
    return switch (templateId) {
      case 1, 2, 4, 5, 10, 11, 20, 21, 30,
          100, 110, 120, 125, 130, 140, 145, 155, 156,
          200, 202, 210, 212, 220, 222, 230, 232, 240, 242,
          280, 281, 282, 283, 300, 310, 312, 314, 320, 322, 324, 326 -> templateId;
      default -> throw new StarbaseProtocolException(
          "unknown order-entry templateId: " + templateId);
    };
  }

  /**
   * Classifies messages that can mutate order/session state. Unknown IDs fail closed rather than
   * being treated as harmless.
   */
  public static boolean isStateChanging(int templateId) {
    requireKnown(templateId);
    return switch (templateId) {
      case 10, 11, 20, 21, 30, 155, 156, 280, 281, 282, 283 -> false;
      default -> true;
    };
  }

  private OrderEntryTemplateDispatch() {}
}
