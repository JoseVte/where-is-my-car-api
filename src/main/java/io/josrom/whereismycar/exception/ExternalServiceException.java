package io.josrom.whereismycar.exception;

import org.springframework.http.HttpStatus;

public class ExternalServiceException extends ApiException {
    public ExternalServiceException(String messageKey, Object... messageArgs) {
        super(HttpStatus.BAD_GATEWAY, messageKey, messageArgs);
    }
}
