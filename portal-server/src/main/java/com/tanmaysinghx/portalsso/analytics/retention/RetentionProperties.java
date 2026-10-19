package com.tanmaysinghx.portalsso.analytics.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-provided first-run defaults for the admin-managed retention windows. */
@ConfigurationProperties(prefix = "app.analytics.retention")
public record RetentionProperties(
        Integer loginEventsDays,
        Integer loginEventsHours,
        Integer auditEventsDays,
        Integer auditEventsHours,
        Integer batchSize,
        String cron) {

    public RetentionProperties {
        if (loginEventsHours == null && loginEventsDays == null) loginEventsHours = 48;
        if (batchSize == null || batchSize <= 0) batchSize = 1000;
        if (cron == null || cron.isBlank()) cron = "0 0 * * * *";
    }

    public boolean isLoginEventRetentionEnabled() {
        return effectiveHours(loginEventsHours, loginEventsDays) > 0;
    }

    public boolean isAuditEventRetentionEnabled() {
        return effectiveHours(auditEventsHours, auditEventsDays) > 0;
    }

    private static int effectiveHours(Integer hours, Integer days) {
        if (hours != null && hours > 0) return hours;
        if (days != null && days > 0) return Math.multiplyExact(days, 24);
        return 0;
    }
}
