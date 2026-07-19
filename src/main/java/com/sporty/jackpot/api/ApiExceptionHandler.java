package com.sporty.jackpot.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sporty.jackpot.exception.BetNotFoundException;

/**
 * Translates exceptions into response bodies: bean-validation failures and unknown bets.
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

    /**
     * Turns an unknown {@code betId} into 404. A bet is persisted only once it has been consumed
     * and contributed, so an id with no bet behind it is a request about a resource that does not
     * exist — not a bet that lost. Reporting it as a loss would be a lie the caller cannot detect,
     * and would silently mask a typo'd id or a bet still queued for processing.
     */
    @ExceptionHandler(BetNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse onBetNotFound(BetNotFoundException ex) {
        return new ErrorResponse("BET_NOT_FOUND", ex.getMessage());
    }

    /** 400 body: an error code plus field name to message. */
    public record ValidationErrorResponse(String error, Map<String, String> fieldErrors) {
    }

    /** Generic error body: a stable machine-readable code plus a human-readable message. */
    public record ErrorResponse(String error, String message) {
    }
}
