package com.tanmaysinghx.portalsso.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for the first administrator, supplied by the operator or auto-generated.
 *
 * <p>When credentials are not configured, autoGenerate (if enabled) automatically provisions an
 * initial administrator with a high-entropy password saved to secrets/initialAdminPassword,
 * mirroring Jenkins LTS first-run behavior.
 *
 * @param adminEmail e.g. {@code APP_BOOTSTRAP_ADMIN_EMAIL}
 * @param adminPassword e.g. {@code APP_BOOTSTRAP_ADMIN_PASSWORD}
 * @param autoGenerate e.g. {@code APP_BOOTSTRAP_AUTO_GENERATE} (default false in test profile)
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String adminEmail, String adminPassword, Boolean autoGenerate) {

    public boolean isConfigured() {
        return adminEmail != null && !adminEmail.isBlank()
                && adminPassword != null && !adminPassword.isBlank();
    }

    public boolean shouldAutoGenerate() {
        return Boolean.TRUE.equals(autoGenerate);
    }
}
