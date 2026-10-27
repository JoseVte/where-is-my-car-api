package io.josrom.whereismycar.controllers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.josrom.whereismycar.dto.ErrorMessage;
import io.josrom.whereismycar.exception.ApiException;
import io.josrom.whereismycar.exception.TooManyRequestsException;

/**
 * Every error response uses one of two shapes, with messages translated to the request locale:
 * {"errors": {"field": "message"}} for validation, {"error": {"message", "className"}} for the rest.
 * Standard Spring MVC errors (malformed JSON, type mismatches, unknown routes, ...) keep their status
 * through ResponseEntityExceptionHandler.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final MessageSource messageSource;

    public ApiExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.status(status).body(Map.of("errors", errors));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        String key = status.is5xxServerError() ? "error.unexpected" : "error.request.invalid";

        return ResponseEntity.status(status).headers(headers).body(error(translate(key), e));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException e) {
        String message = translate(e.getMessageKey(), e.getMessageArgs());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.getStatus());

        if (e instanceof TooManyRequestsException tooMany) {
            // Whole seconds, rounded up, so clients never retry too early
            long seconds = Math.max(1, (tooMany.getRetryAfter().toMillis() + 999) / 1000);
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        }

        return response.body(error(message, e));
    }

    /** Anything unexpected: logged with the stack trace, but the details are not sent to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception e) {
        logger.error("Unexpected error", e);

        return ResponseEntity.status(500).body(error(translate("error.unexpected"), e));
    }

    private String translate(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    private static Map<String, ErrorMessage> error(String message, Exception e) {
        return Map.of("error", new ErrorMessage(message, originOf(e)));
    }

    private static String originOf(Exception e) {
        StackTraceElement[] trace = e.getStackTrace();
        if (trace.length == 0) {
            return null;
        }

        String className = trace[0].getClassName();
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
