package com.tanmaysinghx.portalsso.analytics.web.dto;

import java.time.Instant;
import java.util.List;

/** Everything the dashboard renders, in one response, so the page makes a single request. */
public record DashboardStats(
        String range,
        String bucket,
        Instant from,
        Instant to,
        Totals totals,
        List<TimePoint> signups,
        List<LoginPoint> logins,
        List<LabelCount> lastLoginBuckets,
        List<ClientStat> byClient,
        List<CountryStat> byCountry,
        List<RecentLogin> recentLogins,
        boolean geoDatabaseAvailable) {

    /**
     * @param newUsers accounts created inside the selected window, unlike {@code totalUsers} which
     *     is always the all-time figure — the two answer different questions and a filter that
     *     changed both would make the headline meaningless.
     */
    public record Totals(
            long totalUsers,
            long newUsers,
            long enabledUsers,
            long lockedUsers,
            long totalClients,
            long logins,
            long failedLogins,
            long uniqueIps,
            long countries) {}

    public record TimePoint(String bucket, long count) {}

    public record LoginPoint(String bucket, long successful, long failed) {}

    public record LabelCount(String label, long count) {}

    public record ClientStat(String clientId, String clientName, long logins, long uniqueUsers) {}

    /** @param code ISO 3166-1 alpha-2, null for addresses with no country (loopback, private). */
    public record CountryStat(String code, String name, long logins) {}

    public record RecentLogin(
            String email,
            boolean successful,
            String ipAddress,
            String countryCode,
            String countryName,
            String clientId,
            Instant occurredAt) {}
}
