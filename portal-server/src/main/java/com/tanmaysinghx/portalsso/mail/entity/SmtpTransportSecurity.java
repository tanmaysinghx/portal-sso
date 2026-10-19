package com.tanmaysinghx.portalsso.mail.entity;

/**
 * How the SMTP connection is protected.
 *
 * <p>The stored value is {@link #name()}, so these names are part of the schema — add new ones
 * rather than repurposing an existing one.
 */
public enum SmtpTransportSecurity {

    /**
     * Plaintext SMTP. Only defensible for a relay reached over a private network; the password, if
     * one is configured, crosses the wire in the clear.
     */
    NONE,

    /**
     * Connect in plaintext on the submission port (typically 587) and upgrade with STARTTLS. The
     * usual choice, and the default, because it is what most providers document.
     */
    STARTTLS,

    /** TLS from the first byte, on the implicit-TLS port (typically 465). */
    SSL_TLS
}
