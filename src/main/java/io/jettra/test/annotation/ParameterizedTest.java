package io.jettra.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Denotes that a method is a parameterized test that should be executed
 * multiple times with different sets of arguments.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ParameterizedTest {
    /**
     * Display name pattern for individual invocations.
     */
    String name() default "[{index}] {arguments}";
}
