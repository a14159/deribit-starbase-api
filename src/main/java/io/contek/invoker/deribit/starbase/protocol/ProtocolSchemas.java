package io.contek.invoker.deribit.starbase.protocol;

/** Pinned protocol source metadata. XML resources are reference/test inputs, never runtime codecs. */
public final class ProtocolSchemas {

  public static final String REVIEW_DATE = "2026-07-30";
  public static final String SOURCE_BUNDLE_URL =
      "https://statics.deribit.com/files/deribit-sbe-xmls.zip";

  public static final ProtocolSchema ORDER_ENTRY =
      new ProtocolSchema(
          2101,
          11,
          "1.3",
          "/schema/deribit-sbe-order-api.xml",
          "70B1B297A4D8472CA31C76E97613909B136C0CF4782CB858CAC306696C0C5A89");

  public static final ProtocolSchema MARKET_DATA =
      new ProtocolSchema(
          2102,
          1,
          "1.0",
          "/schema/deribit-sbe-market-data-api.xml",
          "68F52A5FEF08FA2ECD5F217DEDDA94130AB3B6A24F39090CF088F19D072A73E2");

  private ProtocolSchemas() {}
}
