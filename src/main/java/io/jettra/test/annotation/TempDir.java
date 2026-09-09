package io.jettra.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field or method parameter in a test class as a temporary directory.
 * JettraTest Runner automatically provisions a clean directory (Path or File)
 * before test invocation and guarantees recursive deletion after execution.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface TempDir {
}
