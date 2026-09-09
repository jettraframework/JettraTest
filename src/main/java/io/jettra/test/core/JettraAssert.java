package io.jettra.test.core;

import java.util.Objects;

/**
 * Utility class for assertions in JettraTest.
 */
public class JettraAssert {

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertTrue(boolean condition) {
        assertTrue(condition, "Expected true but was false");
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertFalse(boolean condition) {
        assertFalse(condition, "Expected false but was true");
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }

    public static void assertEquals(Object expected, Object actual) {
        assertEquals(expected, actual, "Values are not equal");
    }

    public static void assertNotEquals(Object unexpected, Object actual, String message) {
        if (Objects.equals(unexpected, actual)) {
            throw new AssertionError(message + " - Did not expect: " + unexpected + ", but was equal");
        }
    }

    public static void assertNotEquals(Object unexpected, Object actual) {
        assertNotEquals(unexpected, actual, "Values are equal");
    }

    public static void assertNotNull(Object object, String message) {
        if (object == null) {
            throw new AssertionError(message);
        }
    }

    public static void assertNotNull(Object object) {
        assertNotNull(object, "Expected non-null but was null");
    }

    public static void assertNull(Object object, String message) {
        if (object != null) {
            throw new AssertionError(message + " - Expected null but was: " + object);
        }
    }

    public static void assertNull(Object object) {
        assertNull(object, "Expected null but was non-null");
    }

    @SuppressWarnings("unchecked")
    public static <T> T assertInstanceOf(Class<T> expectedType, Object obj, String message) {
        assertNotNull(expectedType, "expectedType must not be null");
        if (!expectedType.isInstance(obj)) {
            throw new AssertionError(message + " - Expected instance of: " + expectedType.getName() + " but was: " + (obj != null ? obj.getClass().getName() : "null"));
        }
        return (T) obj;
    }

    public static <T> T assertInstanceOf(Class<T> expectedType, Object obj) {
        return assertInstanceOf(expectedType, obj, "Object is not an instance of expected type");
    }

    @FunctionalInterface
    public interface Executable {
        void execute() throws Throwable;
    }

    public static void assertDoesNotThrow(Executable executable, String message) {
        try {
            executable.execute();
        } catch (Throwable t) {
            throw new AssertionError(message + " - Unexpected exception thrown: " + t.getMessage(), t);
        }
    }

    public static void assertDoesNotThrow(Executable executable) {
        assertDoesNotThrow(executable, "Execution should not throw any exception");
    }
}
