package io.contek.invoker.deribit.starbase.rest;

/** Minimal structure-aware JSON member extraction for REST envelope fields. */
final class JsonEnvelope {

  static String member(String json, String name) {
    if (json == null) {
      return null;
    }
    int length = json.length();
    int depth = 0;
    boolean string = false;
    boolean escaped = false;
    for (int index = 0; index < length; index++) {
      char current = json.charAt(index);
      if (string) {
        if (escaped) escaped = false;
        else if (current == '\\') escaped = true;
        else if (current == '"') string = false;
        continue;
      }
      if (current == '"') {
        int end = stringEnd(json, index + 1);
        if (depth == 1 && json.regionMatches(index + 1, name, 0, name.length())
            && end == index + name.length() + 1) {
          int colon = skipWhitespace(json, end + 1);
          if (colon < length && json.charAt(colon) == ':') {
            int start = skipWhitespace(json, colon + 1);
            int valueEnd = valueEnd(json, start);
            return valueEnd < 0 ? null : json.substring(start, valueEnd);
          }
        }
        index = end;
      } else if (current == '{' || current == '[') depth++;
      else if (current == '}' || current == ']') depth--;
    }
    return null;
  }

  static String stringValue(String json) {
    if (json == null || json.length() < 2 || json.charAt(0) != '"') return null;
    StringBuilder decoded = new StringBuilder(json.length() - 2);
    boolean escaped = false;
    for (int index = 1; index < json.length() - 1; index++) {
      char current = json.charAt(index);
      if (!escaped && current == '\\') {
        escaped = true;
      } else {
        if (escaped) {
          current = switch (current) {
            case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
            case 'b' -> '\b'; case 'f' -> '\f'; default -> current;
          };
        }
        decoded.append(current);
        escaped = false;
      }
    }
    return escaped ? null : decoded.toString();
  }

  private static int stringEnd(String json, int start) {
    boolean escaped = false;
    for (int index = start; index < json.length(); index++) {
      char current = json.charAt(index);
      if (escaped) escaped = false;
      else if (current == '\\') escaped = true;
      else if (current == '"') return index;
    }
    return json.length();
  }

  private static int skipWhitespace(String json, int index) {
    while (index < json.length() && Character.isWhitespace(json.charAt(index))) index++;
    return index;
  }

  private static int valueEnd(String json, int start) {
    if (start >= json.length()) return -1;
    char first = json.charAt(start);
    if (first == '"') {
      int end = stringEnd(json, start + 1);
      return end < json.length() ? end + 1 : -1;
    }
    if (first == '{' || first == '[') {
      char close = first == '{' ? '}' : ']';
      int depth = 0;
      boolean string = false;
      boolean escaped = false;
      for (int index = start; index < json.length(); index++) {
        char current = json.charAt(index);
        if (string) {
          if (escaped) escaped = false;
          else if (current == '\\') escaped = true;
          else if (current == '"') string = false;
        } else if (current == '"') string = true;
        else if (current == first) depth++;
        else if (current == close && --depth == 0) return index + 1;
      }
      return -1;
    }
    int end = start;
    while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
    while (end > start && Character.isWhitespace(json.charAt(end - 1))) end--;
    return end;
  }

  private JsonEnvelope() {}
}
