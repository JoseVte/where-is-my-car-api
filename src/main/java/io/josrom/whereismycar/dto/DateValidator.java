package io.josrom.whereismycar.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link ValidDate}. Use {@link #parse} to convert the value, so validation and conversion always
 * accept the same formats.
 */
public class DateValidator implements ConstraintValidator<ValidDate, String> {
    /** ISO 8601 with offset and optional fraction, for example 2026-01-15T10:30:00Z or 2026-01-15T10:30:00.123+02:00. */
    public static final String ISO = "ISO";

    private String[] formats;

    @Override
    public void initialize(ValidDate constraintAnnotation) {
        this.formats = constraintAnnotation.formats();
    }

    /** Null is valid (the date is optional). Combine with @NotNull to make it required. */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || parse(value, formats) != null;
    }

    /**
     * Parses the value with the first format that matches. Formats without time zone are read as UTC.
     * Returns null if no format matches.
     */
    public static Instant parse(String value, String... formats) {
        if (value == null) {
            return null;
        }

        for (String format : formats) {
            try {
                if (ISO.equals(format)) {
                    return OffsetDateTime.parse(value).toInstant();
                }

                TemporalAccessor parsed = DateTimeFormatter.ofPattern(format)
                        .parseBest(value, ZonedDateTime::from, OffsetDateTime::from, LocalDateTime::from);

                return switch (parsed) {
                    case ZonedDateTime zoned -> zoned.toInstant();
                    case OffsetDateTime offset -> offset.toInstant();
                    case LocalDateTime local -> local.toInstant(ZoneOffset.UTC);
                    default -> null;
                };
            } catch (DateTimeParseException e) {
                // try the next format
            }
        }

        return null;
    }
}
