package io.jettra.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An argument source for {@link ParameterizedTest} providing literal values.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ValueSource {
    String[] strings() default {};
    int[] ints() default {};
    long[] longs() default {};
    double[] doubles() default {};
    boolean[] booleans() default {};
    Class<?>[] classes() default {};
}
