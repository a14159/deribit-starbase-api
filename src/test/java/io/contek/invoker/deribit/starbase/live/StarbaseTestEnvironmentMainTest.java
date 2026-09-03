package io.contek.invoker.deribit.starbase.live;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.LogonEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public final class StarbaseTestEnvironmentMainTest {

  public void testConfigurationDefaultsToReadOnlyAndRedactsSensitiveDiagnostics() {
    char[] secret = "secret-456".toCharArray();
    Map<String, String> environment = requiredEnvironment();

    try (StarbaseTestEnvironmentMain.Configuration configuration =
        StarbaseTestEnvironmentMain.Configuration.from(environment, secret)) {
      assertFalse(configuration.stateChangesEnabled());
      assertEquals(14_210, configuration.sbePortA());
      assertEquals(24_210, configuration.sbePortB());
      assertEquals(14_410, configuration.restPortA());
      assertEquals(24_410, configuration.restPortB());

      String sanitized =
          configuration.sanitize(
              "failed for account-123:secret-456 at vpce-private.example\r\nnext-line");
      assertFalse(sanitized.contains("account-123"));
      assertFalse(sanitized.contains("secret-456"));
      assertFalse(sanitized.contains("vpce-private.example"));
      assertFalse(sanitized.contains("\r"));
      assertFalse(sanitized.contains("\n"));
    }
  }

  public void testStateChangingChecksRequireExactRiskAcknowledgementAndOrderInputs() {
    Map<String, String> environment = requiredEnvironment();
    environment.put("STARBASE_ALLOW_STATE_CHANGES", "true");
    char[] secret = "secret-456".toCharArray();

    assertThrows(
        IllegalArgumentException.class,
        () -> StarbaseTestEnvironmentMain.Configuration.from(environment, secret));

    environment.put(
        "STARBASE_STATE_CHANGE_ACKNOWLEDGEMENT",
        StarbaseTestEnvironmentMain.STATE_CHANGE_ACKNOWLEDGEMENT);
    environment.put("STARBASE_TEST_INSTRUMENT_ID", "101");
    environment.put("STARBASE_TEST_PRICE_MANTISSA", "1");
    environment.put("STARBASE_TEST_QUANTITY_MANTISSA", "1");
    environment.put("STARBASE_TEST_QUANTITY_EXPONENT", "0");
    environment.put("STARBASE_TEST_SIDE", "BUY");

    try (StarbaseTestEnvironmentMain.Configuration configuration =
        StarbaseTestEnvironmentMain.Configuration.from(environment, secret)) {
      assertTrue(configuration.stateChangesEnabled());
      assertEquals(101L, configuration.testInstrumentId());
      assertEquals(1L, configuration.testPriceMantissa());
      assertEquals((byte) 1, configuration.testSide());
    }
  }

  public void testTestnetLogonUsesVersionFourteenInHeaderAndBody() {
    ByteBuffer buffer = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
    int encoded =
        StarbaseTestEnvironmentMain.encodeTestnetLogon(
            buffer,
            "account-123".toCharArray(),
            "secret-456".toCharArray(),
            7L,
            6L,
            5L);

    assertEquals(104, encoded);
    assertEquals(LogonEncoder.TEMPLATE_ID, TcpHeaderCodec.messageTypeId(buffer, 0));
    assertEquals(14, TcpHeaderCodec.version(buffer, 0));
    assertEquals(
        14,
        Short.toUnsignedInt(
            buffer.getShort(TcpHeaderCodec.ENCODED_LENGTH + 16 + 48 + 1)));
  }

  private static Map<String, String> requiredEnvironment() {
    Map<String, String> environment = new HashMap<>();
    environment.put("STARBASE_HOST", "vpce-private.example");
    environment.put("STARBASE_CLIENT_ID", "account-123");
    return environment;
  }
}
