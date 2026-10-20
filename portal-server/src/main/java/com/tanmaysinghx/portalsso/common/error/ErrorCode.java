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
    /**
     * A signed-in user re-confirming their password got it wrong. Deliberately 400, not 401: the
     * session is still valid, and a 401 would read to a client as "you have been signed out".
     */
    CURRENT_PASSWORD_INCORRECT("PRTL-2012", "The current password is incorrect.", HttpStatus.BAD_REQUEST),
    PASSWORD_UNCHANGED("PRTL-2013", "The new password must be different from the current one.", HttpStatus.BAD_REQUEST),
    DEVICE_NOT_FOUND("PRTL-2014", "The requested device was not found.", HttpStatus.NOT_FOUND),

    // =========================================================================
    // 3000 Series: OAuth2 Client Management
    // =========================================================================
    CLIENT_ALREADY_EXISTS("PRTL-3001", "An OAuth client with this client_id already exists.", HttpStatus.CONFLICT),
    CLIENT_NOT_FOUND("PRTL-3002", "The requested OAuth client was not found.", HttpStatus.NOT_FOUND),
    INVALID_CLIENT_METADATA("PRTL-3003", "Provided OAuth client metadata is invalid.", HttpStatus.BAD_REQUEST),
    CLIENT_SECRET_UNAVAILABLE("PRTL-3004", "A client secret is shown only once, when the client is created.", HttpStatus.BAD_REQUEST),
    APPLICATION_NOT_FOUND("PRTL-3005", "The requested application was not found.", HttpStatus.NOT_FOUND),
    ACCESS_REQUEST_DUPLICATE("PRTL-3006", "A pending access request already exists", HttpStatus.CONFLICT),
    ACCESS_REQUEST_NOT_FOUND("PRTL-3007", "Access request not found", HttpStatus.NOT_FOUND),
    ACCESS_ALREADY_GRANTED("PRTL-3008", "User already has access to this application", HttpStatus.BAD_REQUEST),
    EXTERNAL_REGISTRATION_DISABLED("PRTL-3009", "External registration is not enabled for this application", HttpStatus.FORBIDDEN),
    CONNECT_DISABLED("PRTL-3010", "App connection requests are disabled on this server.", HttpStatus.FORBIDDEN),
    CONNECT_REQUEST_NOT_FOUND("PRTL-3011", "The connection request was not found.", HttpStatus.NOT_FOUND),
    CONNECT_REQUEST_INVALID("PRTL-3012", "The connection request is invalid.", HttpStatus.BAD_REQUEST),
    CONNECT_REQUEST_EXPIRED("PRTL-3013", "The connection request has expired. Start the connection again from the app.", HttpStatus.GONE),
    CONNECT_REQUEST_NOT_REVIEWABLE("PRTL-3014", "The connection request has already been decided.", HttpStatus.CONFLICT),
    CONNECT_REQUEST_PENDING("PRTL-3015", "The connection request is still waiting for an administrator.", HttpStatus.CONFLICT),
    CONNECT_REQUEST_DENIED("PRTL-3016", "An administrator denied the connection request.", HttpStatus.FORBIDDEN),
    CONNECT_REDEEM_REJECTED("PRTL-3017", "The connection code or verifier is invalid, or it has already been used.", HttpStatus.BAD_REQUEST),
    CONNECT_TOO_MANY_PENDING("PRTL-3018", "Too many connection requests are waiting for review. Try again later.", HttpStatus.TOO_MANY_REQUESTS),

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
    /** Fields that are optional while email is switched off and required once it is enabled. */
    INVALID_MAIL_SETTINGS("PRTL-4006", "The email settings are incomplete or invalid.", HttpStatus.BAD_REQUEST),

    // Magic links. Each reason is distinct so the landing page can say what to do next rather than
    // "invalid link" — safe to differentiate, because reaching here means the caller already holds
    // an unguessable token and has nothing left to enumerate.
    MAGIC_LINK_DISABLED("PRTL-4010", "Passwordless sign-in is not enabled on this server.", HttpStatus.FORBIDDEN),
    MAGIC_LINK_INVALID("PRTL-4011", "This sign-in link is not valid.", HttpStatus.BAD_REQUEST),
    MAGIC_LINK_EXPIRED("PRTL-4012", "This sign-in link has expired. Request a new one.", HttpStatus.GONE),
    MAGIC_LINK_ALREADY_USED("PRTL-4013", "This sign-in link has already been used. Request a new one.", HttpStatus.GONE),
    PASSWORD_RESET_INVALID("PRTL-4014", "This password reset link is not valid.", HttpStatus.BAD_REQUEST),
    PASSWORD_RESET_EXPIRED("PRTL-4016", "This password reset link has expired. Request a new one.", HttpStatus.GONE),
    PASSWORD_RESET_ALREADY_USED("PRTL-4017", "This password reset link has already been used. Request a new one.", HttpStatus.GONE),

    // =========================================================================
    // 5000 Series: Server, Database & Internal Errors
    // =========================================================================
    INTERNAL_SERVER_ERROR("PRTL-5000", "An unexpected internal server error occurred.", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR("PRTL-5001", "A database error occurred while processing the request.", HttpStatus.INTERNAL_SERVER_ERROR),
    MAIL_NOT_CONFIGURED("PRTL-5002", "No outbound email relay is configured on this server.", HttpStatus.SERVICE_UNAVAILABLE),
    MAIL_SEND_FAILED("PRTL-5003", "The email could not be sent.", HttpStatus.BAD_GATEWAY);

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
