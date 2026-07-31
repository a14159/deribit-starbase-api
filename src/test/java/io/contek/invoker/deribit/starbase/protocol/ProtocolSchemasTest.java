package io.contek.invoker.deribit.starbase.protocol;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertNotNull;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ProtocolSchemasTest {

  public void testPinnedSchemasMatchReviewedResources() throws IOException, NoSuchAlgorithmException {
    assertSchema(
        ProtocolSchemas.ORDER_ENTRY,
        2101,
        11,
        "1.3",
        "70B1B297A4D8472CA31C76E97613909B136C0CF4782CB858CAC306696C0C5A89");
    assertSchema(
        ProtocolSchemas.MARKET_DATA,
        2102,
        1,
        "1.0",
        "68F52A5FEF08FA2ECD5F217DEDDA94130AB3B6A24F39090CF088F19D072A73E2");
    assertTrue(ProtocolSchemas.SOURCE_BUNDLE_URL.startsWith("https://statics.deribit.com/"));
    assertEquals("2026-07-30", ProtocolSchemas.REVIEW_DATE);
  }

  private static void assertSchema(
      ProtocolSchema schema,
      int schemaId,
      int version,
      String semanticVersion,
      String sha256)
      throws IOException, NoSuchAlgorithmException {
    assertEquals(schemaId, schema.schemaId());
    assertEquals(version, schema.version());
    assertEquals(semanticVersion, schema.semanticVersion());
    assertEquals(sha256, schema.sha256());
    try (InputStream input = ProtocolSchemasTest.class.getResourceAsStream(schema.resourcePath())) {
      assertNotNull(input, schema.resourcePath());
      assertEquals(sha256, HexFormat.of().withUpperCase().formatHex(
          MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
    }
  }
}
