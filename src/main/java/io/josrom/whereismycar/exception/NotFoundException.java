package io.josrom.whereismycar.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    public NotFoundException(String messageKey, Object... messageArgs) {
        super(HttpStatus.NOT_FOUND, messageKey, messageArgs);
    }
}
