package com.tanmaysinghx.portalsso.audit.entity;

/**
 * The closed set of administrative changes worth recording.
 *
 * <p>An enum rather than a free-form string so the log stays filterable: a typo'd action name would
 * quietly create a category nobody thinks to search for. Each constant also carries the kind of
 * thing it acts on, so a call site cannot pair {@code CLIENT_DELETED} with a user target.
 *
 * <p>The stored value is {@link #name()}. Renaming a constant rewrites the meaning of rows already
 * written, so treat these names as part of the schema — add new ones, never repurpose old ones.
 */
public enum AuditAction {

    USER_CREATED(TargetType.USER, "User created"),
    USER_ENABLED(TargetType.USER, "User enabled"),
    USER_DISABLED(TargetType.USER, "User disabled"),
    USER_UNLOCKED(TargetType.USER, "User unlocked"),
    /** An administrator stripping a user's second factor is a security control being removed. */
    USER_MFA_RESET(TargetType.USER, "MFA reset by administrator"),
    /** The actor is the unauthenticated caller, not an administrator — see {@code /api/public/register}. */
    USER_SELF_REGISTERED(TargetType.USER, "User self-registered"),

    /** Which roles a user holds is what grants access, so a change to them is audited on the user. */
    USER_ROLES_CHANGED(TargetType.USER, "User roles changed"),
    /** The first administrator, created by the application at startup from operator configuration. */
    ADMIN_BOOTSTRAPPED(TargetType.USER, "Administrator bootstrapped"),
    USER_LOGIN_SUCCESS(TargetType.USER, "User signed in successfully"),
    USER_LOGIN_FAILED(TargetType.USER, "User sign-in attempt failed"),

    CLIENT_CREATED(TargetType.OAUTH_CLIENT, "OAuth client registered"),
    CLIENT_UPDATED(TargetType.OAUTH_CLIENT, "OAuth client updated"),
    CLIENT_DELETED(TargetType.OAUTH_CLIENT, "OAuth client deleted"),

    ROLE_CREATED(TargetType.ROLE, "Role created"),
    ROLE_UPDATED(TargetType.ROLE, "Role updated"),
    ROLE_DELETED(TargetType.ROLE, "Role deleted"),

    APPLICATION_CREATED(TargetType.APPLICATION, "Application added"),
    APPLICATION_UPDATED(TargetType.APPLICATION, "Application updated"),
    APPLICATION_DELETED(TargetType.APPLICATION, "Application deleted"),
    ACCESS_REQUEST_SUBMITTED(TargetType.APPLICATION, "Access request submitted"),
    ACCESS_REQUEST_APPROVED(TargetType.APPLICATION, "Access request approved"),
    ACCESS_REQUEST_DENIED(TargetType.APPLICATION, "Access request denied"),
    /** An administrator approved an app's "Connect with Portal SSO" request. */
    APP_CONNECT_APPROVED(TargetType.APPLICATION, "App connection approved"),
    APP_CONNECT_DENIED(TargetType.APPLICATION, "App connection denied"),
    /** The app backend collected its credentials; the actor names the approving administrator. */
    APP_CONNECT_REDEEMED(TargetType.APPLICATION, "App connection credentials delivered"),

    /** Where this server sends mail from, and the credentials it uses — never the credentials themselves. */
    SMTP_SETTINGS_UPDATED(TargetType.SYSTEM, "Email (SMTP) settings updated"),
    /** Recorded on failure too: a failed test is how an operator learns a relay stopped accepting mail. */
    SMTP_TEST_EMAIL_SENT(TargetType.SYSTEM, "Test email sent"),

    /** Branding, which notifications are sent, and magic-link policy. */
    EMAIL_SETTINGS_UPDATED(TargetType.SYSTEM, "Email settings updated"),
    /** Retention periods for sign-in attempt and administrative audit history. */
    ANALYTICS_RETENTION_UPDATED(TargetType.SYSTEM, "Log retention settings updated"),
    /** A magic link was issued. Recorded because it is a credential that signs an account in. */
    MAGIC_LINK_REQUESTED(TargetType.USER, "Magic link requested"),
    /** A magic link was redeemed — this is a sign-in, and belongs in the trail as one. */
    MAGIC_LINK_SIGN_IN(TargetType.USER, "Signed in with a magic link"),
    /** A magic link created an account, which is account creation with no administrator involved. */
    MAGIC_LINK_SIGN_UP(TargetType.USER, "Account created with a magic link"),
    /** An account signed in from a browser and operating system it had not used before. */
    NEW_DEVICE_SIGN_IN(TargetType.USER, "Sign-in from a new device"),

    /** The account holder changed their own name. Details name the fields, never the values. */
    USER_PROFILE_UPDATED(TargetType.USER, "Profile updated"),
    /** The account holder changed their own password; their other sessions are signed out with it. */
    USER_PASSWORD_CHANGED(TargetType.USER, "Password changed"),
    /** Self-service enrolment, distinct from {@link #USER_MFA_RESET} which an administrator performs. */
    USER_MFA_ENABLED(TargetType.USER, "Two-factor authentication enabled"),
    USER_MFA_DISABLED(TargetType.USER, "Two-factor authentication disabled"),
    /** The previous set stops working, so this is a credential change like any other. */
    USER_RECOVERY_CODES_REGENERATED(TargetType.USER, "Recovery codes regenerated"),
    /** A remembered device was forgotten, so its next sign-in raises a new-device alert again. */
    USER_DEVICE_FORGOTTEN(TargetType.USER, "Remembered device removed");

    /** What the action operates on. Stored so the log can be filtered by object as well as verb. */
    public enum TargetType {
        USER,
        OAUTH_CLIENT,
        ROLE,
        APPLICATION,
        /** Server-wide configuration that belongs to no single user, client or application. */
        SYSTEM
    }

    private final TargetType targetType;
    private final String label;

    AuditAction(TargetType targetType, String label) {
        this.targetType = targetType;
        this.label = label;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public String getLabel() {
        return label;
    }
}
