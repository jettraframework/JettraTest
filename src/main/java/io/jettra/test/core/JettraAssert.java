package io.jettra.test.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Modern assertion engine for the JettraTest framework.
 * Adheres to Java 25+ idioms with strictly typed, high-performance checks,
 * comprehensive array equality, exception handling, and grouped assertions.
 */
public class JettraAssert {

    @FunctionalInterface
    public interface Executable {
        void execute() throws Throwable;
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message != null ? message : "Expected condition to be true, but was false");
        }
    }

    public static void assertTrue(boolean condition) {
        assertTrue(condition, "Expected true but was false");
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message != null ? message : "Expected condition to be false, but was true");
        }
    }

    public static void assertFalse(boolean condition) {
        assertFalse(condition, "Expected false but was true");
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (expected instanceof Number n1 && actual instanceof Number n2) {
            if (!(n1 instanceof Double || n1 instanceof Float || n2 instanceof Double || n2 instanceof Float)) {
                if (n1.longValue() == n2.longValue()) {
                    return;
                }
            } else {
                if (Double.compare(n1.doubleValue(), n2.doubleValue()) == 0) {
                    return;
                }
            }
        }
        if (!Objects.equals(expected, actual)) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Expected: <" + expected + "> but was: <" + actual + ">");
        }
    }

    public static void assertEquals(Object expected, Object actual) {
        assertEquals(expected, actual, "Values are not equal");
    }

    public static void assertEquals(double expected, double actual, double delta, String message) {
        if (Double.isNaN(expected) && Double.isNaN(actual)) {
            return;
        }
        if (Math.abs(expected - actual) > delta) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Expected: <" + expected + "> but was: <" + actual + "> within delta <" + delta + ">");
        }
    }

    public static void assertEquals(double expected, double actual, double delta) {
        assertEquals(expected, actual, delta, null);
    }

    public static void assertEquals(float expected, float actual, float delta, String message) {
        if (Float.isNaN(expected) && Float.isNaN(actual)) {
            return;
        }
        if (Math.abs(expected - actual) > delta) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Expected: <" + expected + "> but was: <" + actual + "> within delta <" + delta + ">");
        }
    }

    public static void assertEquals(float expected, float actual, float delta) {
        assertEquals(expected, actual, delta, null);
    }

    public static void assertNotEquals(Object unexpected, Object actual, String message) {
        if (Objects.equals(unexpected, actual)) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Did not expect: <" + unexpected + "> but value was equal");
        }
    }

    public static void assertNotEquals(Object unexpected, Object actual) {
        assertNotEquals(unexpected, actual, "Values are equal");
    }

    public static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Expected same instance: <" + expected + "> but was: <" + actual + ">");
        }
    }

    public static void assertSame(Object expected, Object actual) {
        assertSame(expected, actual, "Instances are not the same");
    }

    public static void assertNotSame(Object unexpected, Object actual, String message) {
        if (unexpected == actual) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Expected different instances, but both were identical: <" + actual + ">");
        }
    }

    public static void assertNotSame(Object unexpected, Object actual) {
        assertNotSame(unexpected, actual, "Expected different instances");
    }

    public static void assertNotNull(Object object, String message) {
        if (object == null) {
            throw new AssertionError(message != null ? message : "Expected non-null object, but was null");
        }
    }

    public static void assertNotNull(Object object) {
        assertNotNull(object, "Expected non-null but was null");
    }

    public static void assertNull(Object object, String message) {
        if (object != null) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Expected null but was: <" + object + ">");
        }
    }

    public static void assertNull(Object object) {
        assertNull(object, "Expected null but was non-null");
    }

    @SuppressWarnings("unchecked")
    public static <T> T assertInstanceOf(Class<T> expectedType, Object obj, String message) {
        assertNotNull(expectedType, "expectedType must not be null");
        if (!expectedType.isInstance(obj)) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Expected instance of: " + expectedType.getName()
                    + " but was: " + (obj != null ? obj.getClass().getName() : "null"));
        }
        return (T) obj;
    }

    public static <T> T assertInstanceOf(Class<T> expectedType, Object obj) {
        return assertInstanceOf(expectedType, obj, "Object is not an instance of expected type");
    }

    public static void assertDoesNotThrow(Executable executable, String message) {
        Objects.requireNonNull(executable, "Executable must not be null");
        try {
            executable.execute();
        } catch (Throwable t) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Unexpected exception thrown: " + t.getClass().getName() + ": " + t.getMessage(), t);
        }
    }

    public static void assertDoesNotThrow(Executable executable) {
        assertDoesNotThrow(executable, "Execution should not throw any exception");
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable, String message) {
        Objects.requireNonNull(expectedType, "expectedType must not be null");
        Objects.requireNonNull(executable, "executable must not be null");

        try {
            executable.execute();
        } catch (Throwable actualThrown) {
            if (expectedType.isInstance(actualThrown)) {
                return (T) actualThrown;
            } else {
                String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
                throw new AssertionError(prefix + "Expected " + expectedType.getName()
                        + " to be thrown, but was: " + actualThrown.getClass().getName() + " (" + actualThrown.getMessage() + ")", actualThrown);
            }
        }

        String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
        throw new AssertionError(prefix + "Expected " + expectedType.getName() + " to be thrown, but nothing was thrown.");
    }

    public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable) {
        return assertThrows(expectedType, executable, null);
    }

    // -------------------------------------------------------------
    // Array Assertions
    // -------------------------------------------------------------

    public static void assertArrayEquals(byte[] expected, byte[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Arrays differ. Expected: " + Arrays.toString(expected) + ", Actual: " + Arrays.toString(actual));
        }
    }

    public static void assertArrayEquals(byte[] expected, byte[] actual) {
        assertArrayEquals(expected, actual, "Byte arrays are not equal");
    }

    public static void assertArrayEquals(int[] expected, int[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Arrays differ. Expected: " + Arrays.toString(expected) + ", Actual: " + Arrays.toString(actual));
        }
    }

    public static void assertArrayEquals(int[] expected, int[] actual) {
        assertArrayEquals(expected, actual, "Int arrays are not equal");
    }

    public static void assertArrayEquals(long[] expected, long[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Arrays differ. Expected: " + Arrays.toString(expected) + ", Actual: " + Arrays.toString(actual));
        }
    }

    public static void assertArrayEquals(long[] expected, long[] actual) {
        assertArrayEquals(expected, actual, "Long arrays are not equal");
    }

    public static void assertArrayEquals(double[] expected, double[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Arrays differ. Expected: " + Arrays.toString(expected) + ", Actual: " + Arrays.toString(actual));
        }
    }

    public static void assertArrayEquals(double[] expected, double[] actual) {
        assertArrayEquals(expected, actual, "Double arrays are not equal");
    }

    public static void assertArrayEquals(float[] expected, float[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Arrays differ. Expected: " + Arrays.toString(expected) + ", Actual: " + Arrays.toString(actual));
        }
    }

    public static void assertArrayEquals(float[] expected, float[] actual) {
        assertArrayEquals(expected, actual, "Float arrays are not equal");
    }

    public static void assertArrayEquals(short[] expected, short[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Arrays differ. Expected: " + Arrays.toString(expected) + ", Actual: " + Arrays.toString(actual));
        }
    }

    public static void assertArrayEquals(short[] expected, short[] actual) {
        assertArrayEquals(expected, actual, "Short arrays are not equal");
    }

    public static void assertArrayEquals(char[] expected, char[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Arrays differ. Expected: " + Arrays.toString(expected) + ", Actual: " + Arrays.toString(actual));
        }
    }

    public static void assertArrayEquals(char[] expected, char[] actual) {
        assertArrayEquals(expected, actual, "Char arrays are not equal");
    }

    public static void assertArrayEquals(boolean[] expected, boolean[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Arrays differ. Expected: " + Arrays.toString(expected) + ", Actual: " + Arrays.toString(actual));
        }
    }

    public static void assertArrayEquals(boolean[] expected, boolean[] actual) {
        assertArrayEquals(expected, actual, "Boolean arrays are not equal");
    }

    public static void assertArrayEquals(Object[] expected, Object[] actual, String message) {
        if (!Arrays.deepEquals(expected, actual)) {
            String prefix = (message != null && !message.isBlank()) ? (message + " - ") : "";
            throw new AssertionError(prefix + "Object arrays differ. Expected: " + Arrays.deepToString(expected) + ", Actual: " + Arrays.deepToString(actual));
        }
    }

    public static void assertArrayEquals(Object[] expected, Object[] actual) {
        assertArrayEquals(expected, actual, "Object arrays are not equal");
    }

    // -------------------------------------------------------------
    // Failures & Grouped Assertions
    // -------------------------------------------------------------

    public static void fail(String message, Throwable cause) {
        throw new AssertionError(message != null ? message : "Test failed explicitly", cause);
    }

    public static void fail(String message) {
        fail(message, null);
    }

    public static void fail(Throwable cause) {
        fail(cause != null ? cause.getMessage() : "Failed with exception", cause);
    }

    public static void fail() {
        fail("Test failed explicitly", null);
    }

    public static void assertAll(String heading, Executable... executables) {
        Objects.requireNonNull(executables, "Executables must not be null");
        List<Throwable> failures = new ArrayList<>();
        for (Executable executable : executables) {
            try {
                executable.execute();
            } catch (Throwable t) {
                failures.add(t);
            }
        }
        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (heading != null && !heading.isBlank()) {
                sb.append(heading).append(" - ");
            }
            sb.append(failures.size()).append(" failure(s) encountered:\n");
            for (int i = 0; i < failures.size(); i++) {
                sb.append(" [").append(i + 1).append("] ").append(failures.get(i).getMessage()).append("\n");
            }
            AssertionError error = new AssertionError(sb.toString());
            for (Throwable f : failures) {
                error.addSuppressed(f);
            }
            throw error;
        }
    }

    public static void assertAll(Executable... executables) {
        assertAll(null, executables);
    }

    public static void assertLinesMatch(List<String> expectedLines, List<String> actualLines) {
        assertEquals(expectedLines.size(), actualLines.size(), "Line counts differ");
        for (int i = 0; i < expectedLines.size(); i++) {
            assertEquals(expectedLines.get(i), actualLines.get(i), "Mismatch at line " + i);
        }
    }
}
