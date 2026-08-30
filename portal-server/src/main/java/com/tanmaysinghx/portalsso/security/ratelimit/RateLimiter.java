package com.tanmaysinghx.portalsso.security.ratelimit;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Holds one {@link TokenBucket} per (rule, client) pair.
 *
 * <p><strong>Scope:</strong> buckets live in this JVM. Behind a load balancer each instance
 * enforces the limit independently, so the effective ceiling is the configured rate times the
 * number of instances. That is a deliberate trade: the alternative is a shared store on the hot
 * path of every login, and the per-account lockout already provides the hard stop against
 * credential guessing. Anything stricter belongs in the reverse proxy, which sees all traffic.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RateLimitProperties properties;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    /** @return the seconds a caller must wait, or 0 when the request is allowed. */
    public long checkAndConsume(String ruleName, RateLimitProperties.Rule rule, String clientKey) {
        long now = System.nanoTime();
        String key = ruleName + '|' + clientKey;

        TokenBucket bucket = buckets.get(key);
        if (bucket == null) {
            evictIfOversized(now);
            bucket = buckets.computeIfAbsent(
                    key, k -> new TokenBucket(rule.capacity(), rule.refillPerSecond(), now));
        }

        return bucket.tryConsume(now) ? 0 : bucket.retryAfterSeconds(now);
    }

    /**
     * Drops the least recently used tenth of the map once it hits the cap. Buckets are recreated
     * full, so evicting an idle one only ever grants allowance — it never blocks a legitimate
     * caller.
     */
    private void evictIfOversized(long now) {
        if (buckets.size() < properties.maxKeys()) {
            return;
        }
        int target = Math.max(1, properties.maxKeys() / 10);
        buckets.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().lastUsedNanos()))
                .limit(target)
                .map(Map.Entry::getKey)
                .forEach(buckets::remove);
        log.debug("Rate limiter evicted {} idle buckets (cap {})", target, properties.maxKeys());
    }

    int trackedKeys() {
        return buckets.size();
    }

    void reset() {
        buckets.clear();
    }
}
