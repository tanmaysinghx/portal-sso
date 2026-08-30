package com.tanmaysinghx.portalsso.security.ratelimit;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-endpoint request limits, keyed by client IP.
 *
 * <p>Complements the per-account lockout, which caps guesses against one account but does nothing
 * about volume — a caller spreading attempts across many usernames, or hammering the now-public
 * registration endpoint, never trips it.
 *
 * @param enabled master switch; leave on outside tests.
 * @param maxKeys ceiling on tracked IPs. Without one, an attacker rotating source addresses turns
 *     the limiter itself into a memory-exhaustion vector.
 * @param rules path prefix to limit. Longest matching prefix wins, so a specific path can override
 *     a broader one.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, int maxKeys, Map<String, Rule> rules) {

    public RateLimitProperties {
        if (maxKeys <= 0) {
            maxKeys = 50_000;
        }
        rules = rules == null ? new LinkedHashMap<>() : rules;
    }

    /**
     * @param capacity burst allowance — how many requests can arrive back to back.
     * @param perMinute sustained refill rate.
     */
    public record Rule(int capacity, int perMinute) {
        public double refillPerSecond() {
            return perMinute / 60.0;
        }
    }
}
