package io.contek.invoker.deribit.starbase.rest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict allocating JSON parser for low-frequency REST control responses. */
final class JsonParser {
  private final String input;
  private int index;

  private JsonParser(String input) {
    this.input = input;
  }

  static Object parse(String input) {
    if (input == null) throw new IllegalArgumentException("JSON is null");
    JsonParser parser = new JsonParser(input);
    Object value = parser.value();
    parser.whitespace();
    if (parser.index != input.length()) throw parser.error("trailing JSON content");
    return value;
  }

  private Object value() {
    whitespace();
    if (index == input.length()) throw error("missing JSON value");
    return switch (input.charAt(index)) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> string();
      case 't' -> literal("true", Boolean.TRUE);
      case 'f' -> literal("false", Boolean.FALSE);
      case 'n' -> literal("null", null);
      default -> number();
    };
  }

  private Map<String, Object> object() {
    index++;
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    whitespace();
    if (take('}')) return result;
    while (true) {
      whitespace();
      if (index == input.length() || input.charAt(index) != '"') throw error("object key expected");
      String key = string();
      whitespace();
      require(':');
      Object value = value();
      if (result.containsKey(key)) throw error("duplicate object key: " + key);
      result.put(key, value);
      whitespace();
      if (take('}')) return result;
      require(',');
    }
  }

  private List<Object> array() {
    index++;
    ArrayList<Object> result = new ArrayList<>();
    whitespace();
    if (take(']')) return result;
    while (true) {
      result.add(value());
      whitespace();
      if (take(']')) return result;
      require(',');
    }
  }

  private String string() {
    require('"');
    StringBuilder result = new StringBuilder();
    while (index < input.length()) {
      char current = input.charAt(index++);
      if (current == '"') return result.toString();
      if (current < 0x20) throw error("control character in string");
      if (current != '\\') {
        result.append(current);
        continue;
      }
      if (index == input.length()) throw error("truncated escape");
      char escaped = input.charAt(index++);
      switch (escaped) {
        case '"', '\\', '/' -> result.append(escaped);
        case 'b' -> result.append('\b');
        case 'f' -> result.append('\f');
        case 'n' -> result.append('\n');
        case 'r' -> result.append('\r');
        case 't' -> result.append('\t');
        case 'u' -> result.append(unicode());
        default -> throw error("invalid escape");
      }
    }
    throw error("unterminated string");
  }

  private char unicode() {
    if (index + 4 > input.length()) throw error("truncated unicode escape");
    int value = 0;
    for (int count = 0; count < 4; count++) {
      int digit = Character.digit(input.charAt(index++), 16);
      if (digit < 0) throw error("invalid unicode escape");
      value = value * 16 + digit;
    }
    return (char) value;
  }

  private Object number() {
    int start = index;
    if (take('-') && index == input.length()) throw error("truncated number");
    if (take('0')) {
      if (index < input.length() && Character.isDigit(input.charAt(index))) throw error("leading zero");
    } else {
      digits();
    }
    boolean decimal = false;
    if (take('.')) {
      decimal = true;
      digits();
    }
    if (index < input.length() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
      decimal = true;
      index++;
      if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) index++;
      digits();
    }
    String token = input.substring(start, index);
    try {
      return decimal ? new BigDecimal(token) : Long.valueOf(token);
    } catch (NumberFormatException invalid) {
      throw error("invalid number");
    }
  }

  private void digits() {
    int start = index;
    while (index < input.length() && Character.isDigit(input.charAt(index))) index++;
    if (start == index) throw error("digit expected");
  }

  private Object literal(String expected, Object value) {
    if (!input.regionMatches(index, expected, 0, expected.length())) throw error("invalid literal");
    index += expected.length();
    return value;
  }

  private void whitespace() {
    while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++;
  }

  private boolean take(char expected) {
    if (index < input.length() && input.charAt(index) == expected) {
      index++;
      return true;
    }
    return false;
  }

  private void require(char expected) {
    if (!take(expected)) throw error("expected '" + expected + "'");
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException(message + " at offset " + index);
  }
}
