package com.tanmaysinghx.portalsso.analytics.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The dashboard's time filter. Each range also fixes its own bucket size, so a chart never ends up
 * with 1,800 daily points across five years or four points across a day.
 */
public enum StatsRange {
    DAY(Duration.ofDays(1), ChronoUnit.HOURS, "HOUR"),
    WEEK(Duration.ofDays(7), ChronoUnit.DAYS, "DAY"),
    MONTH(Duration.ofDays(30), ChronoUnit.DAYS, "DAY"),
    YEAR(Duration.ofDays(365), ChronoUnit.MONTHS, "MONTH"),
    FIVE_YEARS(Duration.ofDays(365L * 5), ChronoUnit.MONTHS, "MONTH"),
    ALL(null, ChronoUnit.MONTHS, "MONTH");

    private final Duration window;
    private final ChronoUnit bucket;
    private final String bucketLabel;

    StatsRange(Duration window, ChronoUnit bucket, String bucketLabel) {
        this.window = window;
        this.bucket = bucket;
        this.bucketLabel = bucketLabel;
    }

    /** @return the inclusive start of the window, or {@link Instant#EPOCH} for {@link #ALL}. */
    public Instant from(Instant now) {
        return window == null ? Instant.EPOCH : now.minus(window);
    }

    public ChronoUnit bucket() {
        return bucket;
    }

    public String bucketLabel() {
        return bucketLabel;
    }

    public boolean isAll() {
        return window == null;
    }

    public static StatsRange parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MONTH;
        }
        // Accepts the compact forms a URL is likely to carry as well as the enum names.
        return switch (raw.trim().toUpperCase().replace('-', '_')) {
            case "DAY", "1D", "TODAY" -> DAY;
            case "WEEK", "7D", "1W" -> WEEK;
            case "MONTH", "30D", "1M" -> MONTH;
            case "YEAR", "1Y", "365D" -> YEAR;
            case "FIVE_YEARS", "5Y" -> FIVE_YEARS;
            case "ALL", "MAX" -> ALL;
            default -> MONTH;
        };
    }
}
