package com.tanmaysinghx.portalsso.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Centralized exception handler for all REST controllers.
 * Translates exceptions into standardized ApiErrorResponse models
 * with diagnostic error codes (PRTL-xxxx).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles custom domain PortalExceptions.
     */
    @ExceptionHandler(PortalException.class)
    public ResponseEntity<ApiErrorResponse> handlePortalException(PortalException ex, HttpServletRequest request) {
        log.warn("Business exception [{}] on path [{}]: {}", ex.getErrorCode().getCode(), request.getRequestURI(), ex.getMessage());
        ApiErrorResponse errorResponse = ApiErrorResponse.of(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    /**
     * Handles Spring ResponseStatusException.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        ErrorCode errorCode = ErrorCode.fromHttpStatus(status);
        String message = ex.getReason() != null ? ex.getReason() : errorCode.getDefaultMessage();

        log.warn("ResponseStatusException [{}] status [{}] on path [{}]: {}", errorCode.getCode(), status.value(), request.getRequestURI(), message);
        ApiErrorResponse errorResponse = ApiErrorResponse.of(errorCode, message, request.getRequestURI());
        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handles Spring Security AccessDeniedException (403 Forbidden).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on path [{}]: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse errorResponse = ApiErrorResponse.of(ErrorCode.ACCESS_DENIED, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handles Spring Security AuthenticationException (401 Unauthorized).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed on path [{}]: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse errorResponse = ApiErrorResponse.of(ErrorCode.UNAUTHORIZED, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Handles Jakarta validation ConstraintViolationException.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        List<ValidationErrorDetail> validationErrors = new ArrayList<>();
        ex.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "";
            validationErrors.add(new ValidationErrorDetail(field, violation.getMessage(), violation.getInvalidValue()));
        });

        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                ErrorCode.VALIDATION_FAILED,
                "Request parameter validation failed.",
                request.getRequestURI(),
                validationErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles @Valid method argument validation errors (override of ResponseEntityExceptionHandler).
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String path = getRequestPath(request);
        List<ValidationErrorDetail> validationErrors = new ArrayList<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.add(new ValidationErrorDetail(
                    fieldError.getField(),
                    fieldError.getDefaultMessage(),
                    fieldError.getRejectedValue()
            ));
        }

        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                ErrorCode.VALIDATION_FAILED,
                "Validation failed for " + validationErrors.size() + " field(s).",
                path,
                validationErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles malformed or unreadable JSON request body.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String path = getRequestPath(request);
        log.warn("Malformed HTTP request body on [{}]: {}", path, ex.getMessage());
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                ErrorCode.MALFORMED_REQUEST,
                "Malformed or unreadable JSON request body.",
                path
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles unsupported HTTP methods (405).
     */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String path = getRequestPath(request);
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                ErrorCode.METHOD_NOT_ALLOWED,
                ex.getMessage(),
                path
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse);
    }

    /**
     * Handles unsupported Media Types (415).
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String path = getRequestPath(request);
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                ex.getMessage(),
                path
        );
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(errorResponse);
    }

    /**
     * Catch-all handler for unexpected internal exceptions (500).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled server exception on path [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred. Please contact administrator with code PRTL-5000.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    private static String getRequestPath(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return "";
    }
}
