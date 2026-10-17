package com.tanmaysinghx.portalsso.connect.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * "Connect with Portal SSO": apps register themselves, an administrator approves, and the app's
 * backend receives its credentials directly. No secret is ever copied by a person.
 *
 * @param enabled whether apps may submit connection requests at all.
 * @param pendingTtl how long a request waits for an administrator before it expires.
 * @param redeemTtl how long the app has to collect its credentials once a request is approved.
 * @param codeTtl lifetime of the one-time code delivered to the app's callback.
 * @param maxPending cap on unexpired pending requests. Submission is unauthenticated, so this
 *     bounds how much a flood can grow the table between purges.
 * @param maxRedeemAttempts wrong code/verifier pairs tolerated before the request is locked.
 * @param allowInsecureHttp accept plain {@code http://} app URLs for hosts other than loopback.
 *     Off by default: the delivery code travels in the callback URL.
 */
@ConfigurationProperties(prefix = "app.connect")
public record AppConnectProperties(
        Boolean enabled,
        Duration pendingTtl,
        Duration redeemTtl,
        Duration codeTtl,
        Integer maxPending,
        Integer maxRedeemAttempts,
        boolean allowInsecureHttp) {

    public AppConnectProperties {
        enabled = enabled == null || enabled;
        pendingTtl = pendingTtl != null ? pendingTtl : Duration.ofHours(24);
        redeemTtl = redeemTtl != null ? redeemTtl : Duration.ofHours(1);
        codeTtl = codeTtl != null ? codeTtl : Duration.ofMinutes(10);
        maxPending = maxPending != null ? maxPending : 50;
        maxRedeemAttempts = maxRedeemAttempts != null ? maxRedeemAttempts : 5;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
