package io.contek.invoker.deribit.starbase.protocol;

/** Pinned protocol source metadata. XML resources are reference/test inputs, never runtime codecs. */
public final class ProtocolSchemas {

  public static final String REVIEW_DATE = "2026-09-16";
  public static final String SOURCE_BUNDLE_URL =
      "https://statics.deribit.com/files/deribit-sbe-xmls.zip";
  public static final String ORDER_ENTRY_SOURCE_URL =
      "https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-order-api.xml";
  public static final String MARKET_DATA_SOURCE_URL =
      "https://docs.deribit.com/specifications/deribit-sbe-xmls/deribit-sbe-market-data-api.xml";

  public static final ProtocolSchema ORDER_ENTRY =
      new ProtocolSchema(
          2101,
          16,
          "1.5",
          "/schema/deribit-sbe-order-api.xml",
          "64EBC71CCAC3A01203977718CD524C9476D311ADFF01049312CA0778E57CF559");

  public static final ProtocolSchema MARKET_DATA =
      new ProtocolSchema(
          2102,
          1,
          "1.0",
          "/schema/deribit-sbe-market-data-api.xml",
          "6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE");

  private ProtocolSchemas() {}
}
