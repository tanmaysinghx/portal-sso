package com.tanmaysinghx.portalsso.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Standard API error response format returned by GlobalExceptionHandler.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        boolean success,
        int status,
        String code,
        String message,
        String path,
        Instant timestamp,
        List<ValidationErrorDetail> errors
) {
    public static ApiErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ApiErrorResponse(
                false,
                errorCode.getHttpStatus().value(),
                errorCode.getCode(),
                message != null ? message : errorCode.getDefaultMessage(),
                path,
                Instant.now(),
                List.of()
        );
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String message, String path, List<ValidationErrorDetail> errors) {
        return new ApiErrorResponse(
                false,
                errorCode.getHttpStatus().value(),
                errorCode.getCode(),
                message != null ? message : errorCode.getDefaultMessage(),
                path,
                Instant.now(),
                errors != null ? errors : List.of()
        );
    }

    public static ApiErrorResponse of(int status, String code, String message, String path, List<ValidationErrorDetail> errors) {
        return new ApiErrorResponse(
                false,
                status,
                code,
                message,
                path,
                Instant.now(),
                errors != null ? errors : List.of()
        );
    }
}
