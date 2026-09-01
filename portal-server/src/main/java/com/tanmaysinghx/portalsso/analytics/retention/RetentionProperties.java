package com.tanmaysinghx.portalsso.analytics.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retention for {@code login_events}, which gains a row on every sign-in attempt and previously had
 * nothing to bound it.
 *
 * <p><strong>Disabled by default</strong>, and deliberately so. Turning retention on by default
 * would mean an upgrade silently deleting an operator's authentication history — the record they
 * would reach for while investigating an incident. Unbounded growth is a problem an operator can
 * see coming; data that vanished during an upgrade they did not ask for is not. The capability is
 * one property away, and the README says what a reasonable value looks like.
 *
 * <p>Deliberately not applied to {@code audit_events}. That table exists to answer questions after
 * the fact and is usually subject to a compliance retention period rather than a convenience one;
 * deleting from it should be a separate, explicit decision.
 *
 * @param loginEventsDays delete sign-in events older than this many days. {@code 0} keeps everything.
 * @param batchSize rows deleted per statement. Bounded so a first run against a large table does not
 *     take one enormous lock, which on MySQL would stall sign-ins while it ran.
 * @param cron when to run. Defaults to 03:30 daily, off the hot path.
 */
@ConfigurationProperties(prefix = "app.analytics.retention")
public record RetentionProperties(Integer loginEventsDays, Integer batchSize, String cron) {

    public RetentionProperties {
        if (loginEventsDays == null || loginEventsDays < 0) {
            loginEventsDays = 0;
        }
        if (batchSize == null || batchSize <= 0) {
            batchSize = 1000;
        }
        if (cron == null || cron.isBlank()) {
            cron = "0 30 3 * * *";
        }
    }

    public boolean isLoginEventRetentionEnabled() {
        return loginEventsDays > 0;
    }
}
