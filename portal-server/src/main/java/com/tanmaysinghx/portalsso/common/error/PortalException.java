package com.tanmaysinghx.portalsso.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base unchecked exception for all domain and application-specific errors in Portal SSO.
 */
public class PortalException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public PortalException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
    }

    public PortalException(ErrorCode errorCode, String customMessage) {
        super(customMessage != null ? customMessage : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
    }

    public PortalException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage != null ? customMessage : errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
