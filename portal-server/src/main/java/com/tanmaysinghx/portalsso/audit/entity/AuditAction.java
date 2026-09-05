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

    CLIENT_CREATED(TargetType.OAUTH_CLIENT, "OAuth client registered"),
    CLIENT_UPDATED(TargetType.OAUTH_CLIENT, "OAuth client updated"),
    CLIENT_DELETED(TargetType.OAUTH_CLIENT, "OAuth client deleted"),

    ROLE_CREATED(TargetType.ROLE, "Role created"),
    ROLE_UPDATED(TargetType.ROLE, "Role updated"),
    ROLE_DELETED(TargetType.ROLE, "Role deleted"),

    APPLICATION_CREATED(TargetType.APPLICATION, "Application added"),
    APPLICATION_UPDATED(TargetType.APPLICATION, "Application updated"),
    APPLICATION_DELETED(TargetType.APPLICATION, "Application deleted");

    /** What the action operates on. Stored so the log can be filtered by object as well as verb. */
    public enum TargetType {
        USER,
        OAUTH_CLIENT,
        ROLE,
        APPLICATION
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
