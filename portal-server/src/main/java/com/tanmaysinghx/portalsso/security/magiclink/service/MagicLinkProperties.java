package com.tanmaysinghx.portalsso.security.magiclink.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits on magic links that are not an administrator's to set.
 *
 * <p>Whether magic links are on, and how long one lives, are configured in the console — those are
 * product decisions. These are abuse controls, and they stay properties so that turning the feature
 * on in the console cannot also loosen them.
 *
 * @param maxPerAddressPerWindow how many links one address may be sent per {@code window}. The
 *     per-IP rate limiter cannot do this job: an attacker rotating source addresses to flood one
 *     person's mailbox never trips it, because every request comes from somewhere new.
 * @param window the period {@code maxPerAddressPerWindow} is counted over.
 * @param retention how long consumed and expired tokens are kept before the purge job deletes
 *     them. Not zero: a short tail is what lets an administrator answer "was a link issued for this
 *     address?" after an incident.
 */
@ConfigurationProperties(prefix = "app.magic-link")
public record MagicLinkProperties(
        int maxPerAddressPerWindow, Duration window, Duration retention) {

    public MagicLinkProperties {
        if (maxPerAddressPerWindow <= 0) {
            maxPerAddressPerWindow = 5;
        }
        window = window != null ? window : Duration.ofMinutes(15);
        retention = retention != null ? retention : Duration.ofDays(7);
    }
}
