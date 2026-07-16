/*
 * Copyright (c) 2026 Zakaria El Kotb. All rights reserved.
 *
 * This source code is the exclusive property of Zakaria El Kotb.
 * Unauthorized copying, modification, distribution, or use of this file,
 * via any medium, is strictly prohibited without the prior written
 * permission of the copyright owner.
 *
 * Author: Zakaria El Kotb <elkotbzakaria@gmail.com>
 */
package ma.zakaria.tadbirbudget.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ── Domain exception ─────────────────────────────────────────────────────

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiError> handleCustom(CustomException ex) {
        log.warn("[{}] {}", ex.getStatus().value(), ex.getErrorCode());
        return build(ex.getErrorCode(), ex.getStatus());
    }

    // ── Spring Security ──────────────────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return build(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex) {
        log.warn("Account disabled: {}", ex.getMessage());
        return build(ErrorCode.ACCOUNT_DISABLED, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return build(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
    }

    // ── Input validation — @Valid on controller DTOs ─────────────────────────

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, ErrorCode> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError fe) {
                fieldErrors.put(fe.getField(), resolveFieldErrorCode(fe));
            }
        });

        log.warn("Validation failed: {}", fieldErrors);

        return ResponseEntity.badRequest().body(
                ApiError.builder()
                        .code(ErrorCode.VALIDATION_ERROR)
                        .status(HttpStatus.BAD_REQUEST.value())
                        .timestamp(Instant.now().toString())
                        .fieldErrors(fieldErrors)
                        .build());
    }

    // ── Input validation — @Validated on service / @RequestParam ────────────

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, ErrorCode> fieldErrors = new LinkedHashMap<>();

        ex.getConstraintViolations().forEach(cv -> {
            String field = StreamSupport
                    .stream(cv.getPropertyPath().spliterator(), false)
                    .reduce((first, second) -> second)
                    .map(Object::toString)
                    .orElse("unknown");
            String annotationName = cv.getConstraintDescriptor()
                    .getAnnotation().annotationType().getSimpleName();
            fieldErrors.put(field, resolveConstraintName(annotationName));
        });

        log.warn("Constraint violation: {}", fieldErrors);

        return ResponseEntity.badRequest().body(
                ApiError.builder()
                        .code(ErrorCode.VALIDATION_ERROR)
                        .status(HttpStatus.BAD_REQUEST.value())
                        .timestamp(Instant.now().toString())
                        .fieldErrors(fieldErrors)
                        .build());
    }

    // ── Malformed JSON body ──────────────────────────────────────────────────

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return toObjectResponse(build(ErrorCode.INVALID_REQUEST_BODY, HttpStatus.BAD_REQUEST));
    }

    // ── Wrong HTTP method ────────────────────────────────────────────────────

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Method not supported: {} — allowed: {}", ex.getMethod(), ex.getSupportedHttpMethods());
        return toObjectResponse(build(ErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED));
    }

    // ── No matching route ────────────────────────────────────────────────────

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("No resource found: {}", ex.getMessage());
        return toObjectResponse(build(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    // ── Catch-all ────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<ApiError> build(ErrorCode code, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiError.of(code, status.value()));
    }

    private ResponseEntity<Object> toObjectResponse(ResponseEntity<ApiError> response) {
        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }

    private ErrorCode resolveFieldErrorCode(FieldError error) {
        String[] codes = error.getCodes();
        if (codes == null || codes.length == 0) return ErrorCode.INVALID_VALUE;
        return resolveConstraintName(codes[codes.length - 1]);
    }

    private ErrorCode resolveConstraintName(String constraintName) {
        return switch (constraintName) {
            case "NotBlank", "NotNull", "NotEmpty" -> ErrorCode.REQUIRED;
            case "Email"                            -> ErrorCode.INVALID_EMAIL;
            case "Size", "Length"                   -> ErrorCode.INVALID_SIZE;
            case "Min", "Max",
                 "DecimalMin", "DecimalMax"         -> ErrorCode.OUT_OF_RANGE;
            case "Pattern"                          -> ErrorCode.INVALID_FORMAT;
            case "ValidPassword"                    -> ErrorCode.WEAK_PASSWORD;
            case "Positive", "PositiveOrZero"       -> ErrorCode.MUST_BE_POSITIVE;
            case "Negative", "NegativeOrZero"       -> ErrorCode.MUST_BE_NEGATIVE;
            case "Future", "FutureOrPresent"        -> ErrorCode.MUST_BE_FUTURE;
            case "Past",   "PastOrPresent"          -> ErrorCode.MUST_BE_PAST;
            default                                 -> ErrorCode.INVALID_VALUE;
        };
    }
}