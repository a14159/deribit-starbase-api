package io.contek.invoker.deribit.starbase.testutil;

import java.util.Arrays;
import java.util.Objects;

/** Dependency-free assertions for Surefire's POJO test path. */
public final class TestAssertions {

  @FunctionalInterface
  public interface ThrowingRunnable {
    void run() throws Throwable;
  }

  public static void assertEquals(int expected, int actual) {
    assertEquals(expected, actual, null);
  }

  public static void assertEquals(int expected, int actual, String message) {
    if (expected != actual) {
      throw equalityFailure(expected, actual, message);
    }
  }

  public static void assertEquals(long expected, long actual) {
    assertEquals(expected, actual, null);
  }

  public static void assertEquals(long expected, long actual, String message) {
    if (expected != actual) {
      throw equalityFailure(expected, actual, message);
    }
  }

  public static void assertEquals(long expected, Long actual) {
    assertEquals(expected, actual, null);
  }

  public static void assertEquals(long expected, Long actual, String message) {
    if (actual == null || expected != actual.longValue()) {
      throw equalityFailure(expected, actual, message);
    }
  }

  public static void assertEquals(double expected, double actual) {
    assertEquals(expected, actual, null);
  }

  public static void assertEquals(double expected, double actual, String message) {
    if (Double.compare(expected, actual) != 0) {
      throw equalityFailure(expected, actual, message);
    }
  }

  public static void assertEquals(Object expected, Object actual) {
    assertEquals(expected, actual, null);
  }

  public static void assertEquals(Object expected, Object actual, String message) {
    if (!Objects.equals(expected, actual)) {
      throw equalityFailure(expected, actual, message);
    }
  }

  public static void assertNotEquals(Object unexpected, Object actual) {
    assertNotEquals(unexpected, actual, null);
  }

  public static void assertNotEquals(Object unexpected, Object actual, String message) {
    if (Objects.equals(unexpected, actual)) {
      throw new AssertionError(prefix(message) + "expected values to differ, but both were <" + actual + ">");
    }
  }

  public static void assertSame(Object expected, Object actual) {
    assertSame(expected, actual, null);
  }

  public static void assertSame(Object expected, Object actual, String message) {
    if (expected != actual) {
      throw new AssertionError(prefix(message) + "expected same instance, but references differed");
    }
  }

  public static void assertNotSame(Object unexpected, Object actual) {
    assertNotSame(unexpected, actual, null);
  }

  public static void assertNotSame(Object unexpected, Object actual, String message) {
    if (unexpected == actual) {
      throw new AssertionError(prefix(message) + "expected different instances, but both references were identical");
    }
  }

  public static void assertArrayEquals(char[] expected, char[] actual) {
    assertArrayEquals(expected, actual, null);
  }

  public static void assertArrayEquals(char[] expected, char[] actual, String message) {
    if (!Arrays.equals(expected, actual)) {
      throw equalityFailure(Arrays.toString(expected), Arrays.toString(actual), message);
    }
  }

  public static void assertTrue(boolean condition) {
    assertTrue(condition, null);
  }

  public static void assertTrue(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(prefix(message) + "expected <true> but was <false>");
    }
  }

  public static void assertFalse(boolean condition) {
    assertFalse(condition, null);
  }

  public static void assertFalse(boolean condition, String message) {
    if (condition) {
      throw new AssertionError(prefix(message) + "expected <false> but was <true>");
    }
  }

  public static void assertNull(Object actual) {
    assertNull(actual, null);
  }

  public static void assertNull(Object actual, String message) {
    if (actual != null) {
      throw new AssertionError(prefix(message) + "expected <null> but was <" + actual + ">");
    }
  }

  public static void assertNotNull(Object actual) {
    assertNotNull(actual, null);
  }

  public static void assertNotNull(Object actual, String message) {
    if (actual == null) {
      throw new AssertionError(prefix(message) + "expected a non-null value");
    }
  }

  public static <T extends Throwable> T assertThrows(
      Class<T> expectedType, ThrowingRunnable executable) {
    return assertThrows(expectedType, executable, null);
  }

  public static <T extends Throwable> T assertThrows(
      Class<T> expectedType, ThrowingRunnable executable, String message) {
    try {
      executable.run();
    } catch (Throwable actual) {
      if (expectedType.isInstance(actual)) {
        return expectedType.cast(actual);
      }
      AssertionError failure =
          new AssertionError(
              prefix(message)
                  + "expected exception <"
                  + expectedType.getName()
                  + "> but caught <"
                  + actual.getClass().getName()
                  + ">");
      failure.initCause(actual);
      throw failure;
    }
    throw new AssertionError(
        prefix(message) + "expected exception <" + expectedType.getName() + ">, but nothing was thrown");
  }

  private static AssertionError equalityFailure(Object expected, Object actual, String message) {
    return new AssertionError(
        prefix(message) + "expected <" + expected + "> but was <" + actual + ">");
  }

  private static String prefix(String message) {
    return message == null || message.isEmpty() ? "" : message + " ==> ";
  }

  private TestAssertions() {}
}
