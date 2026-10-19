package com.tanmaysinghx.portalsso.mail.service;

import com.tanmaysinghx.portalsso.mail.entity.SmtpTransportSecurity;

/**
 * A complete set of connection details with the password already decrypted.
 *
 * <p>Separate from the {@code SmtpSettings} entity so one code path can send with either the stored
 * configuration or unsaved values an administrator is still testing, and so the plaintext password
 * exists only as a local that is never persisted or logged.
 *
 * @param password plaintext, or null when the relay takes no credentials.
 */
record SmtpConnection(
        String host,
        int port,
        SmtpTransportSecurity transportSecurity,
        String username,
        String password,
        String fromAddress,
        String fromName,
        String replyToAddress) {

    boolean hasCredentials() {
        return username != null && !username.isBlank();
    }
}
