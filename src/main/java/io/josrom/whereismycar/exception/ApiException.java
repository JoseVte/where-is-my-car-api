package io.josrom.whereismycar.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors that map to a specific HTTP status. The message is a key in
 * messages*.properties, translated in ApiExceptionHandler using the request locale.
 */
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String messageKey;
    private final Object[] messageArgs;

    protected ApiException(HttpStatus status, String messageKey, Object... messageArgs) {
        super(messageKey);
        this.status = status;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
