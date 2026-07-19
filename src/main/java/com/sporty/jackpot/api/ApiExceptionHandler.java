package com.sporty.jackpot.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into response bodies. Currently only bean-validation failures;
 * Phase 5 extends it with a not-found case.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Turns a failed {@code @Valid} binding into 400 with one message per offending field, so the
     * caller learns every problem at once rather than fixing them one request at a time.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse onValidationFailure(MethodArgumentNotValidException ex) {
        // LinkedHashMap: field order stays stable, which keeps the response readable and testable
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return new ValidationErrorResponse("VALIDATION_FAILED", errors);
    }

    /** 400 body: an error code plus field name to message. */
    public record ValidationErrorResponse(String error, Map<String, String> fieldErrors) {
    }
}
