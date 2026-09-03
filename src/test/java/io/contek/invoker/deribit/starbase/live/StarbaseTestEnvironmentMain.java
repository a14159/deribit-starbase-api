package io.contek.invoker.deribit.starbase.live;

import io.contek.invoker.deribit.starbase.StarbaseApiFactory;
import io.contek.invoker.deribit.starbase.codec.common.TcpHeaderCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.HeartbeatCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.LogonConfirmationCodec;
import io.contek.invoker.deribit.starbase.codec.orderentry.LogonEncoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.SessionRejectDecoder;
import io.contek.invoker.deribit.starbase.codec.orderentry.TestRequestCodec;
import io.contek.invoker.deribit.starbase.common.GatewaySide;
import io.contek.invoker.deribit.starbase.common.IoPolicy;
import io.contek.invoker.deribit.starbase.common.NanoClock;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import io.contek.invoker.deribit.starbase.marketdata.FeedDiagnostics;
import io.contek.invoker.deribit.starbase.marketdata.StarbaseMarketDataApi;
import io.contek.invoker.deribit.starbase.marketdata.StarbaseMarketDataContext;
import io.contek.invoker.deribit.starbase.rest.StarbaseInstrumentFilter;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestApi;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestContext;
import io.contek.invoker.deribit.starbase.rest.StarbaseRestCredentials;
import java.io.Console;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * EC2-only live validation entry point for the Deribit Starbase test environment.
 *
 * <p>This class deliberately lives in test sources. Credentials are accepted only from the
 * process environment or an interactive password prompt, are never printed, and are erased from
 * mutable local copies. The testnet SBE v15 probe is bounded and sends no order messages.
 */
public final class StarbaseTestEnvironmentMain {

  static final String STATE_CHANGE_ACKNOWLEDGEMENT =
      "I_ACCEPT_THAT_TEST_ORDERS_CAN_EXECUTE";
  private static final int TESTNET_SCHEMA_VERSION = 15;
  private static final int MAXIMUM_FRAME_BYTES = 65_536;

  public static void main(String[] arguments) {
    if (hasArgument(arguments, "--help")) {
      printHelp();
      return;
    }

    char[] suppliedSecret = readSecret();
    int exitCode;
    try (Configuration configuration =
        Configuration.from(System.getenv(), suppliedSecret)) {
      exitCode = new LiveSuite(configuration).run(!hasArgument(arguments, "--live-only"));
    } catch (Throwable failure) {
      System.err.println(
          "STARBASE_TEST|"
              + Instant.now()
              + "|FATAL|configuration|"
              + safeBootstrapFailure(failure));
      exitCode = 2;
    } finally {
      Arrays.fill(suppliedSecret, '\0');
    }
    System.exit(exitCode);
  }

  static int encodeTestnetLogon(
      ByteBuffer buffer,
      char[] clientId,
      char[] secret,
      long sequence,
      long lastProcessedSequence,
      long sendTimeNanos) {
    int encoded =
        LogonEncoder.encode(
            buffer,
            0,
            clientId,
            secret,
            true,
            TESTNET_SCHEMA_VERSION,
            false,
            sequence,
            lastProcessedSequence,
            sendTimeNanos);
    return encoded;
  }

  static void validateLogonConfirmation(ByteBuffer response, int requestedSchemaVersion)
      throws IOException {
    LogonConfirmationCodec.validate(response, 0);
    int acceptedVersion = LogonConfirmationCodec.schemaVersion(response, 0);
    if (acceptedVersion != requestedSchemaVersion) {
      throw new IOException(
          "gateway confirmed schema "
              + acceptedVersion
              + " after requesting "
              + requestedSchemaVersion);
    }
  }

  static boolean isCorrelatedHeartbeat(ByteBuffer response, long correlationId)
      throws IOException {
    int templateId = TcpHeaderCodec.messageTypeId(response, 0);
    if (templateId != HeartbeatCodec.TEMPLATE_ID) {
      throw new IOException(
          "expected heartbeat after TestRequest but received template " + templateId);
    }
    HeartbeatCodec.validate(response, 0);
    return HeartbeatCodec.correlationId(response, 0) == correlationId;
  }

  private static boolean hasArgument(String[] arguments, String expected) {
    for (String argument : arguments) {
      if (expected.equals(argument)) {
        return true;
      }
    }
    return false;
  }

  private static char[] readSecret() {
    String environmentSecret = System.getenv("STARBASE_CLIENT_SECRET");
    if (environmentSecret != null && !environmentSecret.isEmpty()) {
      return environmentSecret.toCharArray();
    }
    Console console = System.console();
    if (console == null) {
      throw new IllegalArgumentException(
          "STARBASE_CLIENT_SECRET is required when no interactive console is available");
    }
    char[] prompted = console.readPassword("Starbase client secret: ");
    if (prompted == null || prompted.length == 0) {
      throw new IllegalArgumentException("a non-empty Starbase client secret is required");
    }
    return prompted;
  }

  private static String safeBootstrapFailure(Throwable failure) {
    String message = failure.getMessage();
    return failure.getClass().getSimpleName()
        + (message == null ? "" : ": " + oneLine(message));
  }

  private static String oneLine(String value) {
    return value.replace('\r', ' ').replace('\n', ' ');
  }

  private static void printHelp() {
    System.out.println(
        """
        Deribit Starbase test-environment validation

        Required:
          STARBASE_HOST                 Assigned AWS PrivateLink DNS name or Route53 alias
          STARBASE_CLIENT_ID            Starbase client ID (maximum 16 ASCII characters)
          STARBASE_CLIENT_SECRET        Optional when attached to an interactive terminal;
                                        otherwise required (maximum 48 ASCII characters)

        Optional:
          STARBASE_SBE_A_PORT           default 14210
          STARBASE_SBE_B_PORT           default 24210
          STARBASE_REST_A_PORT          default 14410
          STARBASE_REST_B_PORT          default 24410
          STARBASE_CONNECT_TIMEOUT_SECONDS default 5
          STARBASE_RESPONSE_TIMEOUT_SECONDS default 15
          STARBASE_MD_INTERFACE         EC2 network interface name; omit to skip multicast
          STARBASE_MD_SECONDS           default 20
          STARBASE_MD_INCREMENTAL_GROUP default 224.0.12.225
          STARBASE_MD_INCREMENTAL_PORT  default 4220
          STARBASE_MD_SNAPSHOT_GROUP    default 224.0.12.224
          STARBASE_MD_SNAPSHOT_PORT     default 4230
          STARBASE_MD_RETRANSMIT_HOST   default 195.138.37.139 (not contacted by this test)
          STARBASE_MD_RETRANSMIT_PORT   default 4240

        State-changing inputs are parsed only behind both of these settings:
          STARBASE_ALLOW_STATE_CHANGES=true
          STARBASE_STATE_CHANGE_ACKNOWLEDGEMENT=I_ACCEPT_THAT_TEST_ORDERS_CAN_EXECUTE

        The current official test environment and production assembly both use SBE v15.
        This runner remains non-trading: it does not submit an order even when the explicit
        state-change acknowledgement and inputs are supplied.

        Use --live-only to skip the Maven regression suite. Use --help to print this text.
        """);
  }

  static final class Configuration implements AutoCloseable {

    private final String host;
    private final char[] clientId;
    private final char[] secret;
    private final int sbePortA;
    private final int sbePortB;
    private final int restPortA;
    private final int restPortB;
    private final Duration connectTimeout;
    private final Duration responseTimeout;
    private final String marketDataInterface;
    private final Duration marketDataDuration;
    private final String incrementalGroup;
    private final int incrementalPort;
    private final String snapshotGroup;
    private final int snapshotPort;
    private final String retransmitHost;
    private final int retransmitPort;
    private final ProductGroup productGroup;
    private final boolean stateChangesEnabled;
    private final long testInstrumentId;
    private final long testPriceMantissa;
    private final long testQuantityMantissa;
    private final int testQuantityExponent;
    private final byte testSide;

    private Configuration(
        String host,
        char[] clientId,
        char[] secret,
        int sbePortA,
        int sbePortB,
        int restPortA,
        int restPortB,
        Duration connectTimeout,
        Duration responseTimeout,
        String marketDataInterface,
        Duration marketDataDuration,
        String incrementalGroup,
        int incrementalPort,
        String snapshotGroup,
        int snapshotPort,
        String retransmitHost,
        int retransmitPort,
        ProductGroup productGroup,
        boolean stateChangesEnabled,
        long testInstrumentId,
        long testPriceMantissa,
        long testQuantityMantissa,
        int testQuantityExponent,
        byte testSide) {
      this.host = host;
      this.clientId = clientId;
      this.secret = secret;
      this.sbePortA = sbePortA;
      this.sbePortB = sbePortB;
      this.restPortA = restPortA;
      this.restPortB = restPortB;
      this.connectTimeout = connectTimeout;
      this.responseTimeout = responseTimeout;
      this.marketDataInterface = marketDataInterface;
      this.marketDataDuration = marketDataDuration;
      this.incrementalGroup = incrementalGroup;
      this.incrementalPort = incrementalPort;
      this.snapshotGroup = snapshotGroup;
      this.snapshotPort = snapshotPort;
      this.retransmitHost = retransmitHost;
      this.retransmitPort = retransmitPort;
      this.productGroup = productGroup;
      this.stateChangesEnabled = stateChangesEnabled;
      this.testInstrumentId = testInstrumentId;
      this.testPriceMantissa = testPriceMantissa;
      this.testQuantityMantissa = testQuantityMantissa;
      this.testQuantityExponent = testQuantityExponent;
      this.testSide = testSide;
    }

    static Configuration from(Map<String, String> environment, char[] suppliedSecret) {
      Objects.requireNonNull(environment, "environment");
      Objects.requireNonNull(suppliedSecret, "suppliedSecret");
      String host = required(environment, "STARBASE_HOST");
      validateHost(host, "STARBASE_HOST");
      char[] clientId = required(environment, "STARBASE_CLIENT_ID").toCharArray();
      char[] secret = suppliedSecret.clone();
      try {
        validateAscii(clientId, 16, "STARBASE_CLIENT_ID");
        validateAscii(secret, 48, "STARBASE_CLIENT_SECRET");

        boolean stateChanges = strictBoolean(environment, "STARBASE_ALLOW_STATE_CHANGES", false);
        long instrumentId = Long.MIN_VALUE;
        long priceMantissa = Long.MIN_VALUE;
        long quantityMantissa = Long.MIN_VALUE;
        int quantityExponent = 0;
        byte side = 0;
        if (stateChanges) {
          String acknowledgement =
              required(environment, "STARBASE_STATE_CHANGE_ACKNOWLEDGEMENT");
          if (!STATE_CHANGE_ACKNOWLEDGEMENT.equals(acknowledgement)) {
            throw new IllegalArgumentException(
                "STARBASE_STATE_CHANGE_ACKNOWLEDGEMENT does not match the required phrase");
          }
          instrumentId = parseLong(environment, "STARBASE_TEST_INSTRUMENT_ID");
          priceMantissa = parseLong(environment, "STARBASE_TEST_PRICE_MANTISSA");
          quantityMantissa = parseLong(environment, "STARBASE_TEST_QUANTITY_MANTISSA");
          quantityExponent =
              parseInt(
                  environment,
                  "STARBASE_TEST_QUANTITY_EXPONENT",
                  Byte.MIN_VALUE + 1,
                  Byte.MAX_VALUE);
          String configuredSide = required(environment, "STARBASE_TEST_SIDE");
          side = switch (configuredSide.toUpperCase(Locale.ROOT)) {
            case "BUY" -> 1;
            case "SELL" -> -1;
            default ->
                throw new IllegalArgumentException("STARBASE_TEST_SIDE must be BUY or SELL");
          };
          if (instrumentId < 0 || priceMantissa == Long.MIN_VALUE || quantityMantissa < 1) {
            throw new IllegalArgumentException("invalid state-changing order inputs");
          }
        }

        return new Configuration(
            host,
            clientId,
            secret,
            parsePort(environment, "STARBASE_SBE_A_PORT", 14_210),
            parsePort(environment, "STARBASE_SBE_B_PORT", 24_210),
            parsePort(environment, "STARBASE_REST_A_PORT", 14_410),
            parsePort(environment, "STARBASE_REST_B_PORT", 24_410),
            parseSeconds(environment, "STARBASE_CONNECT_TIMEOUT_SECONDS", 5),
            parseSeconds(environment, "STARBASE_RESPONSE_TIMEOUT_SECONDS", 15),
            optional(environment, "STARBASE_MD_INTERFACE"),
            parseSeconds(environment, "STARBASE_MD_SECONDS", 20),
            host(environment, "STARBASE_MD_INCREMENTAL_GROUP", "224.0.12.225"),
            parsePort(environment, "STARBASE_MD_INCREMENTAL_PORT", 4_220),
            host(environment, "STARBASE_MD_SNAPSHOT_GROUP", "224.0.12.224"),
            parsePort(environment, "STARBASE_MD_SNAPSHOT_PORT", 4_230),
            host(environment, "STARBASE_MD_RETRANSMIT_HOST", "195.138.37.139"),
            parsePort(environment, "STARBASE_MD_RETRANSMIT_PORT", 4_240),
            productGroup(environment),
            stateChanges,
            instrumentId,
            priceMantissa,
            quantityMantissa,
            quantityExponent,
            side);
      } catch (RuntimeException failure) {
        Arrays.fill(clientId, '\0');
        Arrays.fill(secret, '\0');
        throw failure;
      }
    }

    int sbePortA() {
      return sbePortA;
    }

    int sbePortB() {
      return sbePortB;
    }

    int restPortA() {
      return restPortA;
    }

    int restPortB() {
      return restPortB;
    }

    boolean stateChangesEnabled() {
      return stateChangesEnabled;
    }

    long testInstrumentId() {
      return testInstrumentId;
    }

    long testPriceMantissa() {
      return testPriceMantissa;
    }

    long testQuantityMantissa() {
      return testQuantityMantissa;
    }

    int testQuantityExponent() {
      return testQuantityExponent;
    }

    byte testSide() {
      return testSide;
    }

    char[] copyClientId() {
      return clientId.clone();
    }

    char[] copySecret() {
      return secret.clone();
    }

    String sanitize(String value) {
      if (value == null) {
        return "";
      }
      String sanitized = value;
      sanitized = replaceSensitive(sanitized, host);
      sanitized = replaceSensitive(sanitized, new String(clientId));
      sanitized = replaceSensitive(sanitized, new String(secret));
      return oneLine(sanitized);
    }

    URI restBaseUri(int port) {
      try {
        return new URI("http", null, host, port, "/", null, null);
      } catch (URISyntaxException invalid) {
        throw new IllegalArgumentException("invalid REST endpoint configuration", invalid);
      }
    }

    InetSocketAddress endpoint(int port) {
      return new InetSocketAddress(host, port);
    }

    @Override
    public void close() {
      Arrays.fill(clientId, '\0');
      Arrays.fill(secret, '\0');
    }

    private static String replaceSensitive(String value, String sensitive) {
      return sensitive.isEmpty() ? value : value.replace(sensitive, "<redacted>");
    }

    private static String required(Map<String, String> environment, String name) {
      String value = optional(environment, name);
      if (value == null) {
        throw new IllegalArgumentException(name + " is required");
      }
      return value;
    }

    private static String optional(Map<String, String> environment, String name) {
      String value = environment.get(name);
      return value == null || value.isBlank() ? null : value.trim();
    }

    private static String host(
        Map<String, String> environment, String name, String defaultValue) {
      String value = optional(environment, name);
      value = value == null ? defaultValue : value;
      validateHost(value, name);
      return value;
    }

    private static void validateHost(String value, String name) {
      if (value.indexOf('/') >= 0
          || value.indexOf(':') >= 0
          || value.chars().anyMatch(Character::isWhitespace)) {
        throw new IllegalArgumentException(
            name + " must be a host or IPv4 address without a scheme");
      }
    }

    private static void validateAscii(char[] value, int maximum, String name) {
      if (value.length == 0 || value.length > maximum) {
        throw new IllegalArgumentException(name + " length must be 1.." + maximum);
      }
      for (char character : value) {
        if (character < 0x20 || character > 0x7e) {
          throw new IllegalArgumentException(name + " must contain printable ASCII only");
        }
      }
    }

    private static int parsePort(
        Map<String, String> environment, String name, int defaultValue) {
      String value = optional(environment, name);
      int parsed = value == null ? defaultValue : parseInteger(value, name);
      if (parsed < 1 || parsed > 65_535) {
        throw new IllegalArgumentException(name + " must be in 1..65535");
      }
      return parsed;
    }

    private static Duration parseSeconds(
        Map<String, String> environment, String name, int defaultValue) {
      String value = optional(environment, name);
      int parsed = value == null ? defaultValue : parseInteger(value, name);
      if (parsed < 1 || parsed > 300) {
        throw new IllegalArgumentException(name + " must be in 1..300 seconds");
      }
      return Duration.ofSeconds(parsed);
    }

    private static int parseInt(
        Map<String, String> environment, String name, int minimum, int maximum) {
      int parsed = parseInteger(required(environment, name), name);
      if (parsed < minimum || parsed > maximum) {
        throw new IllegalArgumentException(name + " is out of range");
      }
      return parsed;
    }

    private static long parseLong(Map<String, String> environment, String name) {
      try {
        return Long.parseLong(required(environment, name));
      } catch (NumberFormatException invalid) {
        throw new IllegalArgumentException(name + " must be an integer", invalid);
      }
    }

    private static int parseInteger(String value, String name) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException invalid) {
        throw new IllegalArgumentException(name + " must be an integer", invalid);
      }
    }

    private static boolean strictBoolean(
        Map<String, String> environment, String name, boolean defaultValue) {
      String value = optional(environment, name);
      if (value == null) {
        return defaultValue;
      }
      if ("true".equalsIgnoreCase(value)) {
        return true;
      }
      if ("false".equalsIgnoreCase(value)) {
        return false;
      }
      throw new IllegalArgumentException(name + " must be true or false");
    }

    private static ProductGroup productGroup(Map<String, String> environment) {
      String value = optional(environment, "STARBASE_PRODUCT_GROUP");
      try {
        return value == null
            ? ProductGroup.BTC
            : ProductGroup.valueOf(value.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException invalid) {
        throw new IllegalArgumentException(
            "STARBASE_PRODUCT_GROUP must be BTC, ETH, TIER_2, or TIER_3", invalid);
      }
    }
  }

  private static final class LiveSuite {

    private final Configuration configuration;
    private final Reporter reporter;

    private LiveSuite(Configuration configuration) {
      this.configuration = configuration;
      reporter = new Reporter(configuration);
    }

    private int run(boolean runLocalTests) {
      reporter.info(
          "configuration",
          "host=<redacted>, ports="
              + configuration.sbePortA
              + ","
              + configuration.sbePortB
              + ","
              + configuration.restPortA
              + ","
              + configuration.restPortB
              + ", stateChanges="
              + configuration.stateChangesEnabled);

      if (runLocalTests) {
        runLocalRegressionSuite();
      } else {
        reporter.skip("local-regression-suite", "disabled by --live-only");
      }

      tcpReachability("sbe-a-connect", configuration.sbePortA);
      tcpReachability("sbe-b-connect", configuration.sbePortB);
      tcpReachability("rest-a-connect", configuration.restPortA);
      tcpReachability("rest-b-connect", configuration.restPortB);

      RestApis restApis = runRestChecks();
      try {
        runSbeProbe("sbe-a-v15-logon", configuration.sbePortA);
        runSbeProbe("sbe-b-v15-logon", configuration.sbePortB);
        runMarketDataCheck();
        reportStateChangingPhase();
      } finally {
        if (restApis != null) {
          restApis.close();
        }
      }

      return reporter.finish();
    }

    private void runLocalRegressionSuite() {
      Path root = repositoryRoot();
      if (root == null) {
        reporter.fail(
            "local-regression-suite",
            "could not find pom.xml and Maven Wrapper above the working directory");
        return;
      }
      boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
      String wrapper = windows ? "mvnw.cmd" : "./mvnw";
      ProcessBuilder builder = new ProcessBuilder(wrapper, "test");
      builder.directory(root.toFile());
      builder.inheritIO();
      builder.environment().keySet().removeIf(name -> name.startsWith("STARBASE_"));
      try {
        int status = builder.start().waitFor();
        if (status == 0) {
          reporter.pass("local-regression-suite", "Maven test completed successfully");
        } else {
          reporter.fail("local-regression-suite", "Maven exited with status " + status);
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        reporter.fail("local-regression-suite", interrupted);
      } catch (IOException failure) {
        reporter.fail("local-regression-suite", failure);
      }
    }

    private Path repositoryRoot() {
      Path candidate = Path.of("").toAbsolutePath().normalize();
      while (candidate != null) {
        if (Files.isRegularFile(candidate.resolve("pom.xml"))
            && (Files.isRegularFile(candidate.resolve("mvnw"))
                || Files.isRegularFile(candidate.resolve("mvnw.cmd")))) {
          return candidate;
        }
        candidate = candidate.getParent();
      }
      return null;
    }

    private void tcpReachability(String name, int port) {
      try (Socket socket = new Socket()) {
        socket.connect(configuration.endpoint(port), timeoutMillis(configuration.connectTimeout));
        reporter.pass(name, "TCP connection succeeded on configured port " + port);
      } catch (Throwable failure) {
        reporter.fail(name, failure);
      }
    }

    private RestApis runRestChecks() {
      StarbaseRestApi sideA = null;
      StarbaseRestApi sideB = null;
      try {
        sideA = restApi(configuration.restPortA);
        sideB = restApi(configuration.restPortB);
        implementationInstruments("rest-a-implementation-instruments", sideA);
        implementationInstruments("rest-b-implementation-instruments", sideB);
        basicInstruments("rest-a-basic-instruments", configuration.restPortA);
        basicInstruments("rest-b-basic-instruments", configuration.restPortB);
        implementationOpenOrders(sideA);
        return new RestApis(sideA, sideB);
      } catch (Throwable failure) {
        if (sideA != null) {
          sideA.close();
        }
        if (sideB != null) {
          sideB.close();
        }
        reporter.fail("rest-client-construction", failure);
        return null;
      }
    }

    private StarbaseRestApi restApi(int port) {
      StarbaseRestContext context =
          new StarbaseRestContext(
              configuration.restBaseUri(port),
              configuration.connectTimeout,
              configuration.responseTimeout,
              NanoClock.SYSTEM);
      char[] secret = configuration.copySecret();
      try (StarbaseRestCredentials credentials = new StarbaseRestCredentials(secret)) {
        return new StarbaseApiFactory().rest(context, credentials);
      } finally {
        Arrays.fill(secret, '\0');
      }
    }

    private void implementationInstruments(String name, StarbaseRestApi api) {
      try {
        int count = api.getInstruments(StarbaseInstrumentFilter.ALL, null).size();
        reporter.pass(name, "implementation parsed " + count + " instruments without auth");
      } catch (Throwable failure) {
        reporter.fail(name, failure);
      }
    }

    private void basicInstruments(String name, int port) {
      char[] clientId = configuration.copyClientId();
      char[] secret = configuration.copySecret();
      char[] combined = new char[clientId.length + 1 + secret.length];
      byte[] plain = null;
      byte[] encoded = null;
      try {
        System.arraycopy(clientId, 0, combined, 0, clientId.length);
        combined[clientId.length] = ':';
        System.arraycopy(secret, 0, combined, clientId.length + 1, secret.length);
        plain = new String(combined).getBytes(StandardCharsets.US_ASCII);
        encoded = Base64.getEncoder().encode(plain);
        String authorization = "Basic " + new String(encoded, StandardCharsets.US_ASCII);
        URI uri =
            configuration
                .restBaseUri(port)
                .resolve("api/v2/public/get_instruments");
        HttpClient client =
            HttpClient.newBuilder().connectTimeout(configuration.connectTimeout).build();
        HttpRequest request =
            HttpRequest.newBuilder(uri)
                .timeout(configuration.responseTimeout)
                .header("Accept", "application/json")
                .header("Authorization", authorization)
                .GET()
                .build();
        HttpResponse<Void> response =
            client.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          reporter.pass(name, "HTTP Basic request succeeded with status " + response.statusCode());
        } else {
          reporter.fail(name, "HTTP Basic request returned status " + response.statusCode());
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        reporter.fail(name, interrupted);
      } catch (Throwable failure) {
        reporter.fail(name, failure);
      } finally {
        Arrays.fill(clientId, '\0');
        Arrays.fill(secret, '\0');
        Arrays.fill(combined, '\0');
        if (plain != null) {
          Arrays.fill(plain, (byte) 0);
        }
        if (encoded != null) {
          Arrays.fill(encoded, (byte) 0);
        }
      }
    }

    private void implementationOpenOrders(StarbaseRestApi sideA) {
      try {
        int count = sideA.getOpenOrders().size();
        reporter.pass(
            "rest-a-implementation-open-orders",
            "implementation Bearer request parsed " + count + " open orders");
      } catch (Throwable failure) {
        reporter.fail("rest-a-implementation-open-orders", failure);
      }
    }

    private void runSbeProbe(String name, int port) {
      char[] clientId = configuration.copyClientId();
      char[] secret = configuration.copySecret();
      ByteBuffer request = ByteBuffer.allocateDirect(128).order(ByteOrder.LITTLE_ENDIAN);
      byte[] outbound = null;
      ByteBuffer response = null;
      try (Socket socket = new Socket()) {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.connect(configuration.endpoint(port), timeoutMillis(configuration.connectTimeout));
        socket.setSoTimeout(timeoutMillis(configuration.responseTimeout));

        long epochNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
        int length = encodeTestnetLogon(request, clientId, secret, 1L, 0L, epochNanos);
        outbound = new byte[length];
        request.get(0, outbound, 0, length);
        OutputStream output = socket.getOutputStream();
        output.write(outbound);
        output.flush();

        response = readFrame(socket.getInputStream());
        int templateId = TcpHeaderCodec.messageTypeId(response, 0);
        int headerVersion = TcpHeaderCodec.version(response, 0);
        if (templateId == LogonConfirmationCodec.TEMPLATE_ID) {
          validateLogonConfirmation(response, TESTNET_SCHEMA_VERSION);
          int acceptedVersion = LogonConfirmationCodec.schemaVersion(response, 0);
          int heartbeat = LogonConfirmationCodec.heartbeatIntervalSeconds(response, 0);
          verifyHeartbeatRoundTrip(socket, TcpHeaderCodec.sequenceNumber(response, 0));
          reporter.pass(
              name,
              "authenticated; schema="
                  + acceptedVersion
                  + ", logonHeaderVersion="
                  + headerVersion
                  + ", heartbeatSeconds="
                  + heartbeat
                  + ", heartbeatRoundTrip=true");
          return;
        }
        if (templateId == SessionRejectDecoder.TEMPLATE_ID) {
          SessionRejectDecoder.validate(response, 0);
          int reason = SessionRejectDecoder.reason(response, 0);
          int detailsLength = SessionRejectDecoder.detailsLength(response, 0);
          reporter.fail(
              name,
              "session reject reason=" + reason + ", detailBytes=" + detailsLength);
          return;
        }
        reporter.fail(
            name,
            "unexpected pre-authentication template="
                + templateId
                + ", headerVersion="
                + headerVersion);
      } catch (Throwable failure) {
        reporter.fail(name, failure);
      } finally {
        Arrays.fill(clientId, '\0');
        Arrays.fill(secret, '\0');
        zero(request);
        if (outbound != null) {
          Arrays.fill(outbound, (byte) 0);
        }
        if (response != null) {
          zero(response);
        }
      }
    }

    private void verifyHeartbeatRoundTrip(Socket socket, long lastProcessedSequence)
        throws IOException {
      final long correlationId = 7_710_014L;
      ByteBuffer request = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
      byte[] outbound = null;
      try {
        int length =
            TestRequestCodec.encode(
                request,
                0,
                correlationId,
                2L,
                lastProcessedSequence,
                TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()));
        outbound = new byte[length];
        request.get(0, outbound, 0, length);
        OutputStream output = socket.getOutputStream();
        output.write(outbound);
        output.flush();

        for (int observed = 0; observed < 8; observed++) {
          ByteBuffer response = readFrame(socket.getInputStream());
          try {
            if (isCorrelatedHeartbeat(response, correlationId)) {
              return;
            }
          } finally {
            zero(response);
          }
        }
        throw new IOException("no correlated heartbeat arrived after TestRequest");
      } finally {
        zero(request);
        if (outbound != null) {
          Arrays.fill(outbound, (byte) 0);
        }
      }
    }

    private void runMarketDataCheck() {
      if (configuration.marketDataInterface == null) {
        reporter.skip(
            "market-data-multicast",
            "STARBASE_MD_INTERFACE is unset; configure AWS multicast/IGMPv2 and the EC2 "
                + "interface to run it");
        reporter.skip(
            "market-data-retransmit",
            "official test retransmit is unavailable over the AWS path");
        return;
      }
      StarbaseMarketDataContext context =
          new StarbaseMarketDataContext(
              configuration.productGroup,
              GatewaySide.A,
              configuration.marketDataInterface,
              new InetSocketAddress(configuration.incrementalGroup, configuration.incrementalPort),
              new InetSocketAddress(configuration.snapshotGroup, configuration.snapshotPort),
              new InetSocketAddress(configuration.retransmitHost, configuration.retransmitPort),
              1 << 20,
              1 << 16,
              configuration.responseTimeout,
              IoPolicy.BLOCKING,
              NanoClock.SYSTEM);
      try (StarbaseMarketDataApi api = new StarbaseApiFactory().marketData(context)) {
        api.start();
        long deadline = System.nanoTime() + configuration.marketDataDuration.toNanos();
        while (System.nanoTime() - deadline < 0 && api.transportFailure() == null) {
          Thread.sleep(100L);
        }
        Throwable failure = api.transportFailure();
        if (failure != null) {
          reporter.fail("market-data-multicast", failure);
        } else if (!api.isTransportOpen()) {
          reporter.fail("market-data-multicast", "UDP receivers are not open");
        } else if (api.receivedPacketCount() == 0L) {
          reporter.fail(
              "market-data-multicast",
              "receivers opened but no packet arrived during "
                  + configuration.marketDataDuration.toSeconds()
                  + " seconds");
        } else {
          FeedDiagnostics incremental = api.incrementalDiagnostics();
          FeedDiagnostics snapshot = api.snapshotDiagnostics();
          reporter.pass(
              "market-data-multicast",
              "packets="
                  + api.receivedPacketCount()
                  + ", loops="
                  + api.receiverLoopCount()
                  + ", incrementalMessages="
                  + incremental.messages()
                  + ", snapshotMessages="
                  + snapshot.messages()
                  + ", incrementalHealth="
                  + incremental.health()
                  + ", snapshotHealth="
                  + snapshot.health());
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        reporter.fail("market-data-multicast", interrupted);
      } catch (Throwable failure) {
        reporter.fail("market-data-multicast", failure);
      }
      reporter.skip(
          "market-data-retransmit",
          "official test retransmit is unavailable over the AWS path; it was not contacted");
    }

    private void reportStateChangingPhase() {
      if (!configuration.stateChangesEnabled) {
        reporter.skip(
            "order-new-amend-cancel",
            "state changes are disabled (safe default)");
        return;
      }
      reporter.blocked(
          "order-new-amend-cancel",
          "explicit acknowledgement was accepted, but this bounded non-trading runner does not "
              + "submit orders");
    }

    private ByteBuffer readFrame(InputStream input) throws IOException {
      byte[] prefix = new byte[4];
      readFully(input, prefix, 0, prefix.length);
      int messageLength =
          Short.toUnsignedInt(
              ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN).getShort(2));
      if (messageLength < TcpHeaderCodec.ENCODED_LENGTH || messageLength > MAXIMUM_FRAME_BYTES) {
        throw new IOException("invalid inbound SBE message length " + messageLength);
      }
      int alignedLength = (messageLength + 7) & ~7;
      byte[] frame = new byte[alignedLength];
      System.arraycopy(prefix, 0, frame, 0, prefix.length);
      readFully(input, frame, prefix.length, alignedLength - prefix.length);
      ByteBuffer buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
      TcpHeaderCodec.validateFrame(buffer, 0);
      return buffer;
    }

    private void readFully(InputStream input, byte[] target, int offset, int length)
        throws IOException {
      int read = 0;
      while (read < length) {
        int count = input.read(target, offset + read, length - read);
        if (count < 0) {
          throw new EOFException("gateway closed before a complete SBE frame arrived");
        }
        read += count;
      }
    }

    private static int timeoutMillis(Duration duration) {
      return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, duration.toMillis()));
    }

    private static void zero(ByteBuffer buffer) {
      for (int index = 0; index < buffer.capacity(); index++) {
        buffer.put(index, (byte) 0);
      }
    }
  }

  private static final class RestApis implements AutoCloseable {

    private final StarbaseRestApi sideA;
    private final StarbaseRestApi sideB;

    private RestApis(StarbaseRestApi sideA, StarbaseRestApi sideB) {
      this.sideA = sideA;
      this.sideB = sideB;
    }

    @Override
    public void close() {
      try {
        sideA.close();
      } finally {
        sideB.close();
      }
    }
  }

  private static final class Reporter {

    private final Configuration configuration;
    private int passed;
    private int failed;
    private int skipped;
    private int blocked;

    private Reporter(Configuration configuration) {
      this.configuration = configuration;
    }

    private void pass(String test, String detail) {
      passed++;
      log("PASS", test, detail);
    }

    private void fail(String test, Throwable failure) {
      String message = failure.getMessage();
      fail(
          test,
          failure.getClass().getSimpleName()
              + (message == null || message.isBlank() ? "" : ": " + message));
    }

    private void fail(String test, String detail) {
      failed++;
      log("FAIL", test, detail);
    }

    private void skip(String test, String detail) {
      skipped++;
      log("SKIP", test, detail);
    }

    private void blocked(String test, String detail) {
      blocked++;
      log("BLOCKED", test, detail);
    }

    private void info(String test, String detail) {
      log("INFO", test, detail);
    }

    private int finish() {
      log(
          "SUMMARY",
          "suite",
          "passed="
              + passed
              + ", failed="
              + failed
              + ", skipped="
              + skipped
              + ", blocked="
              + blocked);
      return failed == 0 ? 0 : 1;
    }

    private void log(String status, String test, String detail) {
      System.out.println(
          "STARBASE_TEST|"
              + Instant.now()
              + "|"
              + status
              + "|"
              + test
              + "|"
              + configuration.sanitize(detail));
    }
  }

  private StarbaseTestEnvironmentMain() {}
}
