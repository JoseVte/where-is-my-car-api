package io.josrom.whereismycar.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated String must be a date in one of {@link #formats()}: {@link DateValidator#ISO} or a
 * DateTimeFormatter pattern (quote literals, for example yyyy-MM-dd'T'HH:mm). Null is valid.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateValidator.class)
public @interface ValidDate {
    String message() default "{validation.date.format}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    String[] formats() default {DateValidator.ISO};
}
