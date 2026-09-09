package io.jettra.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An argument source for {@link ParameterizedTest} that provides enum constants.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EnumSource {
    /**
     * The enum class supplying the constants.
     */
    Class<? extends Enum<?>> value();

    /**
     * Specific names of enum constants to include or exclude.
     */
    String[] names() default {};

    /**
     * The mode determining how {@link #names()} should be matched.
     */
    Mode mode() default Mode.INCLUDE;

    enum Mode {
        INCLUDE,
        EXCLUDE
    }
}
