package io.josrom.whereismycar.exception;

import java.time.Duration;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApiException {
    private final Duration retryAfter;

    public TooManyRequestsException(String messageKey, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, messageKey);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
