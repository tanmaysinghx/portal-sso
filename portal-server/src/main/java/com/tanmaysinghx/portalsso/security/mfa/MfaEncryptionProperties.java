package com.tanmaysinghx.portalsso.security.mfa;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keys protecting stored TOTP secrets.
 *
 * <p>There is deliberately <strong>no default</strong>. This service previously fell back to a key
 * written into its own source, which for a self-hosted product means every deployment that did not
 * override it encrypted under a key anyone reading the repository already knew — a database dump was
 * a complete second-factor bypass, which is the exact failure the encryption exists to prevent.
 *
 * @param encryptionKey the active key. Required before anyone can enrol in MFA; supply it as
 *     {@code APP_SECURITY_MFA_ENCRYPTION_KEY}. Any length — it is stretched to 256 bits by SHA-256.
 * @param previousEncryptionKey set only while rotating. Secrets that still decrypt under it are
 *     re-encrypted with {@code encryptionKey} at startup, after which this can be removed. This is
 *     what makes refusing to start on a missing key a two-minute fix rather than a lockout.
 */
@ConfigurationProperties(prefix = "app.security.mfa")
public record MfaEncryptionProperties(String encryptionKey, String previousEncryptionKey) {

    public boolean hasEncryptionKey() {
        return encryptionKey != null && !encryptionKey.isBlank();
    }

    public boolean hasPreviousEncryptionKey() {
        return previousEncryptionKey != null && !previousEncryptionKey.isBlank();
    }
}
