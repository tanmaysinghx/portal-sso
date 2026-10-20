package com.tanmaysinghx.portalsso.security.passwordreset.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param ttl how long an emailed link works. Short: the link sits in a mailbox, and in the logs of
 *     every gateway and link scanner on the way.
 * @param maxPerAddressPerWindow links one address may be sent per {@code window}.
 * @param window the period the per-address cap is counted over.
 * @param retention how long consumed and expired tokens are kept before the purge job deletes them.
 */
@ConfigurationProperties(prefix = "app.password-reset")
public record PasswordResetProperties(Duration ttl, int maxPerAddressPerWindow, Duration window, Duration retention) {

    public PasswordResetProperties {
        ttl = ttl != null ? ttl : Duration.ofMinutes(30);
        if (maxPerAddressPerWindow <= 0) {
            maxPerAddressPerWindow = 3;
        }
        window = window != null ? window : Duration.ofMinutes(15);
        retention = retention != null ? retention : Duration.ofDays(7);
    }
}
