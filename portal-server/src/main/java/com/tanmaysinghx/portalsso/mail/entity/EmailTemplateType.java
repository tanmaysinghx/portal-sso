package com.tanmaysinghx.portalsso.mail.entity;

/**
 * The messages this server can send.
 *
 * <p>Each constant names a Thymeleaf template pair — {@code templates/email/<template>.html} for
 * the rich part and {@code templates/email/text/<template>.txt} for the plain-text alternative.
 * Every message is sent as both: a text part is what makes mail readable in a client that refuses
 * HTML, and its absence is one of the strongest spam signals there is.
 *
 * <p>{@code name()} is used in the admin API and the audit log, so treat these names as part of the
 * contract — add new ones rather than repurposing an existing one.
 */
public enum EmailTemplateType {

    WELCOME("welcome", "Welcome", "Sent once, when an account is created."),

    NEW_DEVICE_SIGN_IN(
            "new-device",
            "New device sign-in",
            "Security alert sent when an account signs in from a browser and operating system it has not used before."),

    SIGN_IN_NOTICE(
            "sign-in-notice",
            "Every sign-in",
            "Sent on every successful sign-in. Off by default — for a daily-driver account this is a message each morning, which trains people to ignore the alerts that matter."),

    MAGIC_LINK_SIGN_IN(
            "magic-link-sign-in",
            "Magic link sign-in",
            "The passwordless sign-in link, sent when a user asks for one."),

    MAGIC_LINK_SIGN_UP(
            "magic-link-sign-up",
            "Magic link sign-up",
            "The passwordless registration link, which both proves the address and creates the account."),

    PASSWORD_RESET(
            "password-reset",
            "Password reset",
            "The single-use link sent when someone uses \"Forgot password?\". Always on while a relay is configured.");

    private final String template;
    private final String label;
    private final String description;

    EmailTemplateType(String template, String label, String description) {
        this.template = template;
        this.label = label;
        this.description = description;
    }

    /** Base name of the template pair, without directory or extension. */
    public String getTemplate() {
        return template;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
