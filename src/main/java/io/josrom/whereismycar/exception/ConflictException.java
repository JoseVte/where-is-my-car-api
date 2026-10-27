package io.josrom.whereismycar.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(String messageKey, Object... messageArgs) {
        super(HttpStatus.CONFLICT, messageKey, messageArgs);
    }
}
