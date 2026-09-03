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
        15,
        "1.5",
        "4BA2A80B473AC233B6DDB971158E7A65353B1E287C7B304F14062AC2E5E9106C");
    assertSchema(
        ProtocolSchemas.MARKET_DATA,
        2102,
        1,
        "1.0",
        "6875032D595D4F92DABE444ACF9DC9E27B27D34C03E2423403D175D87F8CADCE");
    assertTrue(ProtocolSchemas.ORDER_ENTRY_SOURCE_URL.endsWith("deribit-sbe-order-api.xml"));
    assertTrue(ProtocolSchemas.MARKET_DATA_SOURCE_URL.endsWith("deribit-sbe-market-data-api.xml"));
    assertEquals("2026-09-03", ProtocolSchemas.REVIEW_DATE);
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
