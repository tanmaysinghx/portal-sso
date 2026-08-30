package com.tanmaysinghx.portalsso.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for the first administrator, supplied by the operator.
 *
 * <p>Both default to null and nothing happens unless both are set, so there is never a default
 * administrator and never a password baked into the product. That is the whole design: an account
 * exists only because an operator deliberately asked for one, with a password they chose.
 *
 * @param adminEmail e.g. {@code APP_BOOTSTRAP_ADMIN_EMAIL}
 * @param adminPassword e.g. {@code APP_BOOTSTRAP_ADMIN_PASSWORD}
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String adminEmail, String adminPassword) {

    public boolean isConfigured() {
        return adminEmail != null && !adminEmail.isBlank()
                && adminPassword != null && !adminPassword.isBlank();
    }
}
