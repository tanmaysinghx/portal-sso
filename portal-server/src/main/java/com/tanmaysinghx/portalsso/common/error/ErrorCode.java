package com.tanmaysinghx.portalsso.common.error;

import org.springframework.http.HttpStatus;

/**
 * Standardized application error codes with diagnostic prefixes (PRTL-xxxx)
 * for rapid troubleshooting, logging, and client diagnosis.
 */
public enum ErrorCode {

    // =========================================================================
    // 1000 Series: Authentication, Authorization & Security
    // =========================================================================
    UNAUTHORIZED("PRTL-1001", "Authentication is required to access this resource.", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("PRTL-1002", "Access is denied. You do not have the required permissions.", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS("PRTL-1003", "Invalid email or password.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("PRTL-1004", "The user account is locked.", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED("PRTL-1005", "The user account is disabled.", HttpStatus.FORBIDDEN),
    SESSION_EXPIRED("PRTL-1006", "The current session has expired.", HttpStatus.UNAUTHORIZED),
    INVALID_MFA_CODE("PRTL-1007", "Invalid or expired multi-factor authentication code.", HttpStatus.BAD_REQUEST),
    MFA_REQUIRED("PRTL-1008", "Multi-factor authentication challenge required.", HttpStatus.UNAUTHORIZED),
    MFA_UNAVAILABLE("PRTL-1009", "Multi-factor authentication is not configured on this server.", HttpStatus.SERVICE_UNAVAILABLE),

    // =========================================================================
    // 2000 Series: User Management
    // =========================================================================
    USER_NOT_FOUND("PRTL-2001", "The requested user was not found.", HttpStatus.NOT_FOUND),
    SELF_DISABLE_PROHIBITED("PRTL-2002", "You cannot disable your own account.", HttpStatus.BAD_REQUEST),
    USER_ALREADY_EXISTS("PRTL-2003", "A user with the specified email already exists.", HttpStatus.CONFLICT),
    INVALID_USER_DATA("PRTL-2004", "Provided user data is invalid.", HttpStatus.BAD_REQUEST),
    REGISTRATION_DISABLED("PRTL-2005", "Self-registration is not enabled on this server.", HttpStatus.FORBIDDEN),
    ROLE_NOT_FOUND("PRTL-2006", "The requested role was not found.", HttpStatus.NOT_FOUND),
    ROLE_ALREADY_EXISTS("PRTL-2007", "A role with that name already exists.", HttpStatus.CONFLICT),
    ROLE_PROTECTED("PRTL-2008", "This role is required by the platform and cannot be deleted.", HttpStatus.BAD_REQUEST),
    LAST_ADMIN_PROHIBITED("PRTL-2009", "This would leave the server with no administrator.", HttpStatus.BAD_REQUEST),
    SELF_DEMOTION_PROHIBITED("PRTL-2010", "You cannot remove your own administrator role.", HttpStatus.BAD_REQUEST),
    WEAK_PASSWORD("PRTL-2011", "The password does not meet the configured policy.", HttpStatus.BAD_REQUEST),

    // =========================================================================
    // 3000 Series: OAuth2 Client Management
    // =========================================================================
    CLIENT_ALREADY_EXISTS("PRTL-3001", "An OAuth client with this client_id already exists.", HttpStatus.CONFLICT),
    CLIENT_NOT_FOUND("PRTL-3002", "The requested OAuth client was not found.", HttpStatus.NOT_FOUND),
    INVALID_CLIENT_METADATA("PRTL-3003", "Provided OAuth client metadata is invalid.", HttpStatus.BAD_REQUEST),
    CLIENT_SECRET_UNAVAILABLE("PRTL-3004", "A client secret is shown only once, when the client is created.", HttpStatus.BAD_REQUEST),

    // =========================================================================
    // 4000 Series: Validation, Formatting & Generic Client Errors
    // =========================================================================
    VALIDATION_FAILED("PRTL-4001", "Request validation failed.", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("PRTL-4002", "Malformed or unreadable request body.", HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER("PRTL-4003", "Required request parameter is missing.", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("PRTL-4004", "The requested resource was not found.", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("PRTL-4005", "HTTP method is not supported for this endpoint.", HttpStatus.METHOD_NOT_ALLOWED),
    RESOURCE_CONFLICT("PRTL-4009", "A conflict occurred with existing resource state.", HttpStatus.CONFLICT),
    RATE_LIMIT_EXCEEDED("PRTL-4029", "Too many requests from this address.", HttpStatus.TOO_MANY_REQUESTS),
    UNSUPPORTED_MEDIA_TYPE("PRTL-4015", "Content-Type is not supported.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // =========================================================================
    // 5000 Series: Server, Database & Internal Errors
    // =========================================================================
    INTERNAL_SERVER_ERROR("PRTL-5000", "An unexpected internal server error occurred.", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR("PRTL-5001", "A database error occurred while processing the request.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public static ErrorCode fromHttpStatus(HttpStatus status) {
        if (status == null) {
            return INTERNAL_SERVER_ERROR;
        }
        return switch (status) {
            case BAD_REQUEST -> MALFORMED_REQUEST;
            case UNAUTHORIZED -> UNAUTHORIZED;
            case FORBIDDEN -> ACCESS_DENIED;
            case NOT_FOUND -> RESOURCE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> METHOD_NOT_ALLOWED;
            case CONFLICT -> RESOURCE_CONFLICT;
            case UNSUPPORTED_MEDIA_TYPE -> UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? MALFORMED_REQUEST : INTERNAL_SERVER_ERROR;
        };
    }
}
