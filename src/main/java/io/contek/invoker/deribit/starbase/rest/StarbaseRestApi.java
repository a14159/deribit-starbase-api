package io.contek.invoker.deribit.starbase.rest;

import io.contek.invoker.deribit.starbase.common.AbstractStarbaseApi;
import io.contek.invoker.deribit.starbase.book.InstrumentRegistry;
import io.contek.invoker.deribit.starbase.common.ProductGroup;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.Objects;

/** Blocking Starbase REST utility API for bootstrap, recovery, and administration. */
public final class StarbaseRestApi extends AbstractStarbaseApi {

  private final StarbaseRestContext context;
  private final StarbaseRestTransport transport;

  public StarbaseRestApi(StarbaseRestContext context, StarbaseRestCredentials credentials) {
    this.context = Objects.requireNonNull(context, "context");
    this.transport = new StarbaseRestTransport(context, credentials);
  }

  public StarbaseRestContext context() {
    return context;
  }

  public List<StarbaseInstrument> getInstruments(
      StarbaseInstrumentFilter filter, InstrumentRegistry registry) {
    Objects.requireNonNull(filter, "filter");
    StarbaseRestResponse response =
        transport.get("api/v2/public/get_instruments" + query(filter), false);
    final List<StarbaseInstrument> instruments;
    try {
      Object parsed = JsonParser.parse(response.resultJson());
      if (!(parsed instanceof List<?> values)) throw new IllegalArgumentException("result is not an array");
      ArrayList<StarbaseInstrument> decoded = new ArrayList<>(values.size());
      for (Object value : values) decoded.add(instrument(value));
      instruments = List.copyOf(decoded);
    } catch (IllegalArgumentException invalid) {
      throw new StarbaseRestException(
          "Invalid Starbase instruments response", response.httpStatus(),
          StarbaseRestException.NO_ERROR_CODE, null, false, invalid);
    }
    if (registry != null) {
      HashSet<Long> ids = new HashSet<>();
      HashSet<String> names = new HashSet<>();
      int additions = 0;
      for (StarbaseInstrument instrument : instruments) {
        if (instrument.productGroup() == null) {
          throw new StarbaseRestException(
              "Instrument product_group is required for registry bootstrap",
              response.httpStatus(), StarbaseRestException.NO_ERROR_CODE, null, false, null);
        }
        if (!ids.add(instrument.instrumentId()) || !names.add(instrument.instrumentName())) {
          throw new StarbaseRestException(
              "Duplicate instrument identity in REST response", response.httpStatus(),
              StarbaseRestException.NO_ERROR_CODE, null, false, null);
        }
        registry.validateBootstrapIdentity(
            instrument.instrumentId(), instrument.instrumentName(), instrument.productGroup());
        if (!registry.contains(instrument.instrumentId())) additions++;
      }
      if (registry.size() + additions > registry.maximumEntries()) {
        throw new StarbaseRestException(
            "Instrument registry capacity exhausted", response.httpStatus(),
            StarbaseRestException.NO_ERROR_CODE, null, false, null);
      }
      for (StarbaseInstrument instrument : instruments) {
        registry.bootstrapIdentity(
            instrument.instrumentId(), instrument.instrumentName(), instrument.productGroup());
      }
    }
    return instruments;
  }

  public List<StarbaseOpenOrder> getOpenOrders() {
    StarbaseRestResponse response =
        transport.get("api/v2/private/get_open_orders", true);
    try {
      Object parsed = JsonParser.parse(response.resultJson());
      if (!(parsed instanceof List<?> values)) throw new IllegalArgumentException("result is not an array");
      ArrayList<StarbaseOpenOrder> orders = new ArrayList<>(values.size());
      HashSet<Long> orderIds = new HashSet<>();
      for (Object value : values) {
        StarbaseOpenOrder order = openOrder(value);
        if (!orderIds.add(order.orderId())) {
          throw new IllegalArgumentException("duplicate or ambiguous order_id");
        }
        orders.add(order);
      }
      return List.copyOf(orders);
    } catch (IllegalArgumentException invalid) {
      throw new StarbaseRestException(
          "Invalid Starbase open-orders response", response.httpStatus(),
          StarbaseRestException.NO_ERROR_CODE, null, false, invalid);
    }
  }

  public OpenOrderRecoveryCache openOrderRecoveryCache(Duration refreshInterval) {
    return new OpenOrderRecoveryCache(context.clock(), refreshInterval, this::getOpenOrders);
  }

  public long cancelAll() {
    StarbaseRestResponse response = transport.get("api/v2/private/cancel_all", true);
    try {
      Object result = JsonParser.parse(response.resultJson());
      if (!(result instanceof Long count) || count < 0) {
        throw new IllegalArgumentException("cancel_all result must be a non-negative integer");
      }
      return count;
    } catch (IllegalArgumentException invalid) {
      throw invalidResult("cancel_all", response, invalid);
    }
  }

  public void lockPortfolio() {
    requireOk("lock_portfolio");
  }

  public void unlockPortfolio() {
    requireOk("unlock_portfolio");
  }

  @Override
  protected void onClose() {
    transport.close();
  }

  StarbaseRestTransport transport() {
    return transport;
  }

  private static StarbaseInstrument instrument(Object value) {
    if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("instrument is not an object");
    return new StarbaseInstrument(
        requiredLong(map, "instrument_id"), requiredString(map, "instrument_name"),
        StarbaseInstrumentKind.parse(requiredString(map, "kind")), nullableLong(map, "index_id"),
        productGroup(nullableString(map, "product_group")), nullableString(map, "base_currency"),
        nullableString(map, "quote_currency"), nullableString(map, "settlement_currency"),
        nullableDecimal(map, "tick_size"), nullableDecimal(map, "qty_tick_size"),
        nullableDecimal(map, "strike"),
        nullableString(map, "option_type"), requiredBoolean(map, "is_active"),
        nullableLong(map, "expiration_timestamp"), nullableLong(map, "creation_timestamp"),
        nullableDecimal(map, "min_trade_amount"), nullableDecimal(map, "contract_size"),
        nullableString(map, "settlement_period"), nullableDecimal(map, "maker_commission"),
        nullableDecimal(map, "taker_commission"), nullableDecimal(map, "block_trade_commission"));
  }

  private static StarbaseOpenOrder openOrder(Object value) {
    if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("order is not an object");
    BigDecimal amount = requiredDecimal(map, "amount");
    BigDecimal filledAmount = requiredDecimal(map, "filled_amount");
    if (amount.signum() < 0 || filledAmount.signum() < 0 || filledAmount.compareTo(amount) > 0) {
      throw new IllegalArgumentException("invalid order amount/fill relationship");
    }
    Boolean postOnly = optionalBoolean(map, "post_only");
    Boolean rejectPostOnly = optionalBoolean(map, "reject_post_only");
    Boolean reduceOnly = optionalBoolean(map, "reduce_only");
    if (Boolean.TRUE.equals(postOnly) && Boolean.TRUE.equals(rejectPostOnly)) {
      throw new IllegalArgumentException("post_only and reject_post_only are mutually exclusive");
    }
    return new StarbaseOpenOrder(
        exactOrderId(map), requiredString(map, "instrument_name"),
        StarbaseOrderSide.parse(requiredString(map, "side")), requiredDecimal(map, "price"),
        amount, filledAmount, nullableDecimal(map, "average_price"),
        StarbaseRestOrderState.parse(requiredString(map, "order_state")),
        StarbaseRestOrderType.parse(requiredString(map, "order_type")),
        nullableTimeInForce(map), postOnly, rejectPostOnly, reduceOnly,
        nullableLong(map, "creation_timestamp"),
        nullableLong(map, "last_update_timestamp"), nullableString(map, "label"),
        nullableBoolean(map, "api"), nullableDecimal(map, "max_show"),
        nullableDecimal(map, "profit_loss"), nullableDecimal(map, "commission"));
  }

  private void requireOk(String operation) {
    StarbaseRestResponse response =
        transport.get("api/v2/private/" + operation, true);
    try {
      Object result = JsonParser.parse(response.resultJson());
      if (!"ok".equals(result)) throw new IllegalArgumentException(operation + " result must be ok");
    } catch (IllegalArgumentException invalid) {
      throw invalidResult(operation, response, invalid);
    }
  }

  private static StarbaseRestException invalidResult(
      String operation, StarbaseRestResponse response, IllegalArgumentException cause) {
    return new StarbaseRestException(
        "Invalid Starbase " + operation + " response", response.httpStatus(),
        StarbaseRestException.NO_ERROR_CODE, null, false, cause);
  }

  private static String query(StarbaseInstrumentFilter filter) {
    StringBuilder query = new StringBuilder();
    append(query, "currency", filter.currency());
    append(query, "kind", filter.kind() == null ? null : filter.kind().wireValue());
    append(query, "expired", filter.expired() == null ? null : filter.expired().toString());
    return query.isEmpty() ? "" : "?" + query;
  }

  private static void append(StringBuilder target, String name, String value) {
    if (value == null) return;
    if (!target.isEmpty()) target.append('&');
    target.append(name).append('=').append(percentEncode(value));
  }

  private static String percentEncode(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    StringBuilder encoded = new StringBuilder(bytes.length);
    char[] hex = "0123456789ABCDEF".toCharArray();
    for (byte item : bytes) {
      int unsigned = Byte.toUnsignedInt(item);
      if ((unsigned >= 'a' && unsigned <= 'z') || (unsigned >= 'A' && unsigned <= 'Z')
          || (unsigned >= '0' && unsigned <= '9') || unsigned == '-' || unsigned == '.'
          || unsigned == '_' || unsigned == '~') encoded.append((char) unsigned);
      else encoded.append('%').append(hex[unsigned >>> 4]).append(hex[unsigned & 15]);
    }
    return encoded.toString();
  }

  private static long requiredLong(Map<?, ?> map, String name) {
    Object value = map.get(name);
    if (!(value instanceof Long number)) throw new IllegalArgumentException(name + " must be an int64");
    return number;
  }

  private static Long nullableLong(Map<?, ?> map, String name) {
    Object value = map.get(name);
    if (value == null) return null;
    if (!(value instanceof Long number)) throw new IllegalArgumentException(name + " must be an int64");
    return number;
  }

  private static String requiredString(Map<?, ?> map, String name) {
    String value = nullableString(map, name);
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    return value;
  }

  private static long exactOrderId(Map<?, ?> map) {
    long orderId = Long.parseLong(requiredString(map, "order_id"));
    if (orderId == Long.MIN_VALUE) {
      throw new IllegalArgumentException("order_id is the SBE null sentinel");
    }
    return orderId;
  }

  private static String nullableString(Map<?, ?> map, String name) {
    Object value = map.get(name);
    if (value == null) return null;
    if (!(value instanceof String string)) throw new IllegalArgumentException(name + " must be a string");
    return string;
  }

  private static BigDecimal nullableDecimal(Map<?, ?> map, String name) {
    Object value = map.get(name);
    if (value == null) return null;
    if (value instanceof BigDecimal decimal) return decimal;
    if (value instanceof Long integer) return BigDecimal.valueOf(integer);
    throw new IllegalArgumentException(name + " must be numeric");
  }

  private static BigDecimal requiredDecimal(Map<?, ?> map, String name) {
    BigDecimal value = nullableDecimal(map, name);
    if (value == null) throw new IllegalArgumentException(name + " is required");
    return value;
  }

  private static Boolean nullableBoolean(Map<?, ?> map, String name) {
    Object value = map.get(name);
    if (value == null) return null;
    if (!(value instanceof Boolean bool)) throw new IllegalArgumentException(name + " must be boolean");
    return bool;
  }

  private static Boolean optionalBoolean(Map<?, ?> map, String name) {
    if (!map.containsKey(name)) return null;
    Object value = map.get(name);
    if (!(value instanceof Boolean bool)) throw new IllegalArgumentException(name + " must be boolean");
    return bool;
  }

  private static StarbaseTimeInForce nullableTimeInForce(Map<?, ?> map) {
    String value = nullableString(map, "time_in_force");
    return value == null ? null : StarbaseTimeInForce.parse(value);
  }

  private static boolean requiredBoolean(Map<?, ?> map, String name) {
    Object value = map.get(name);
    if (!(value instanceof Boolean bool)) throw new IllegalArgumentException(name + " must be boolean");
    return bool;
  }

  private static ProductGroup productGroup(String value) {
    if (value == null) return null;
    try {
      return ProductGroup.valueOf(value);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("unknown product_group: " + value, invalid);
    }
  }
}
