package com.library.management.backend.common.error;

import java.util.Comparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The one place that turns an exception into an HTTP response.
 *
 * <p>Controllers therefore contain no try/catch and services throw
 * {@link ApiException} without knowing anything about HTTP. Every branch below
 * produces the same {@link ErrorResponse} shape ({@code API_CONTRACT.md} §3) --
 * including the unexpected ones, so a stack trace can never reach the client.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Deliberate business rejection thrown by a service. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        ErrorCode code = ex.getErrorCode();
        log.debug("Business rule rejected the request: {}", code, ex);
        return build(code, ex.getMessage(), ex.getField());
    }

    /**
     * Bean Validation on an {@code @Valid @RequestBody} DTO.
     *
     * <p>Only one field is reported, and the choice is alphabetical rather than
     * whatever order the validator happened to use: the response must be
     * deterministic so the frontend highlights the same field on every retry.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream()
                .min(Comparator.comparing(FieldError::getField))
                .orElse(null);
        if (fieldError == null) {
            return build(ErrorCode.VALIDATION_ERROR, null, null);
        }
        return build(ErrorCode.VALIDATION_ERROR, fieldError.getDefaultMessage(), fieldError.getField());
    }

    /** Bean Validation on method parameters, e.g. {@code @Positive @PathVariable}. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        MessageSourceResolvable error = ex.getAllErrors().stream()
                .findFirst()
                .orElse(null);
        String field = error instanceof FieldError fe ? fe.getField() : null;
        String message = error != null ? error.getDefaultMessage() : null;
        return build(ErrorCode.VALIDATION_ERROR, message, field);
    }

    /**
     * Body that the JSON parser could not read -- malformed input, so 400.
     *
     * <p>{@code field} stays null: the parser's own path is an internal detail and
     * the frontend cannot act on it any better than on the whole body.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body", ex);
        return build(ErrorCode.VALIDATION_ERROR, "Request body is missing or malformed", null);
    }

    /** Path or query parameter of the wrong type, e.g. {@code /api/categories/abc}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(ErrorCode.VALIDATION_ERROR, "Parameter has the wrong type", ex.getName());
    }

    /** No handler matched the URL. Answered in the contract shape, not Spring's default page. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, null, null);
    }

    /**
     * A constraint the service did not pre-check (or lost a race on).
     *
     * <p>Services map the collisions they know about onto specific codes before
     * this ever fires; reaching here means the database caught something the
     * application did not, which is a conflict rather than a bug in the request.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Unmapped data integrity violation", ex);
        return build(ErrorCode.DATA_INTEGRITY_VIOLATION, null, null);
    }

    /** Last resort: log everything server-side, tell the client nothing. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, null, null);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, String field) {
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, message, field));
    }
}
