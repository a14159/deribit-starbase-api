package io.contek.invoker.deribit.starbase.testutil;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertSame;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertThrows;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import java.io.IOException;

public final class TestAssertionsTest {
  public void testPrimitiveEqualityPreservesNumericTypes() {
    TestAssertions.assertEquals(7, 7);
    TestAssertions.assertEquals(7L, 7L);
    TestAssertions.assertEquals(7L, Long.valueOf(7L));
    TestAssertions.assertEquals(7.25d, 7.25d);

    assertThrows(AssertionError.class, () -> TestAssertions.assertEquals(7, 8));
    assertThrows(AssertionError.class, () -> TestAssertions.assertEquals(7L, 8L));
    assertThrows(
        AssertionError.class,
        () -> TestAssertions.assertEquals(7L, Long.valueOf(8L)));
    assertThrows(AssertionError.class, () -> TestAssertions.assertEquals(7L, (Long) null));
    assertThrows(AssertionError.class, () -> TestAssertions.assertEquals(7.25d, 8.25d));
  }
  public void testObjectEqualityAndInequalityUseValueSemantics() {
    TestAssertions.assertEquals("value", new String("value"));
    TestAssertions.assertNotEquals("left", "right");

    assertThrows(AssertionError.class, () -> TestAssertions.assertEquals("left", "right"));
    assertThrows(AssertionError.class, () -> TestAssertions.assertNotEquals("same", "same"));
  }
  public void testIdentityAssertionsUseReferenceSemantics() {
    Object value = new Object();
    TestAssertions.assertSame(value, value);
    TestAssertions.assertNotSame(value, new Object());

    assertThrows(AssertionError.class, () -> TestAssertions.assertSame(value, new Object()));
    assertThrows(AssertionError.class, () -> TestAssertions.assertNotSame(value, value));
  }
  public void testArrayEqualityComparesContents() {
    TestAssertions.assertArrayEquals(new char[] {'a', 'b'}, new char[] {'a', 'b'});

    assertThrows(
        AssertionError.class,
        () -> TestAssertions.assertArrayEquals(new char[] {'a'}, new char[] {'b'}));
  }
  public void testBooleanAssertionsRejectTheOppositeValue() {
    TestAssertions.assertTrue(true);
    TestAssertions.assertFalse(false);

    assertThrows(AssertionError.class, () -> TestAssertions.assertTrue(false));
    assertThrows(AssertionError.class, () -> TestAssertions.assertFalse(true));
  }
  public void testNullAssertionsDistinguishNullFromNonNull() {
    Object value = new Object();
    TestAssertions.assertNull(null);
    TestAssertions.assertNotNull(value);

    assertThrows(AssertionError.class, () -> TestAssertions.assertNull(value));
    assertThrows(AssertionError.class, () -> TestAssertions.assertNotNull(null));
  }
  public void testFailureMessagesRetainCallerContext() {
    AssertionError failure =
        assertThrows(
            AssertionError.class,
            () -> TestAssertions.assertEquals(1L, 2L, "allocation contract"));

    assertTrue(failure.getMessage().contains("allocation contract"));
    assertTrue(failure.getMessage().contains("expected <1>"));
    assertTrue(failure.getMessage().contains("but was <2>"));
  }
  public void testExpectedExceptionReturnsCaughtInstance() {
    IOException expected = new IOException("expected");

    IOException actual =
        TestAssertions.assertThrows(IOException.class, () -> {
          throw expected;
        });

    assertSame(expected, actual);
  }
  public void testExpectedExceptionFailsWhenNothingIsThrown() {
    AssertionError failure =
        assertThrows(
            AssertionError.class,
            () -> TestAssertions.assertThrows(IOException.class, () -> {}));

    assertTrue(failure.getMessage().contains(IOException.class.getName()));
  }
  public void testExpectedExceptionFailsForWrongType() {
    IllegalStateException wrong = new IllegalStateException("wrong");

    AssertionError failure =
        assertThrows(
            AssertionError.class,
            () -> TestAssertions.assertThrows(IOException.class, () -> {
              throw wrong;
            }));

    assertTrue(failure.getMessage().contains(IOException.class.getName()));
    assertTrue(failure.getMessage().contains(IllegalStateException.class.getName()));
    assertSame(wrong, failure.getCause());
  }
  public void testExpectedExceptionAcceptsCheckedThrowables() {
    Exception expected = new Exception("checked");

    Exception actual =
        TestAssertions.assertThrows(Exception.class, () -> {
          throw expected;
        });

    assertEquals("checked", actual.getMessage());
    assertSame(expected, actual);
  }
}
