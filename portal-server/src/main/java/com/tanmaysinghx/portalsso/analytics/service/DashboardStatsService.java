package com.tanmaysinghx.portalsso.analytics.service;

import com.tanmaysinghx.portalsso.analytics.entity.LoginEvent;
import com.tanmaysinghx.portalsso.analytics.geo.GeoIpResolver;
import com.tanmaysinghx.portalsso.analytics.repository.LoginEventRepository;
import com.tanmaysinghx.portalsso.analytics.web.dto.DashboardStats;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the dashboard payload.
 *
 * <p>Bucketing is done in Java over rows fetched for the window, not with SQL date functions.
 * Those differ across MySQL, H2 and Postgres, and this project has already been burned twice by
 * dialect divergence — a chart that buckets correctly against the test database and wrongly in
 * production would be a particularly quiet bug. The cost is holding one window of rows in memory,
 * which is the right trade at this scale; past it, the fix is a rollup table rather than
 * dialect-specific SQL.
 */
@Service
public class DashboardStatsService {

    private static final int RECENT_LOGIN_LIMIT = 25;
    /** Past this, extra slices stop being distinguishable and the tail folds into "Other". */
    private static final int MAX_CLIENT_SLICES = 8;

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final LoginEventRepository loginEventRepository;
    private final UserRepository userRepository;
    private final OAuthClientRepository oAuthClientRepository;
    private final GeoIpResolver geoIpResolver;

    public DashboardStatsService(
            LoginEventRepository loginEventRepository,
            UserRepository userRepository,
            OAuthClientRepository oAuthClientRepository,
            GeoIpResolver geoIpResolver) {
        this.loginEventRepository = loginEventRepository;
        this.userRepository = userRepository;
        this.oAuthClientRepository = oAuthClientRepository;
        this.geoIpResolver = geoIpResolver;
    }

    @Transactional(readOnly = true)
    public DashboardStats build(StatsRange range) {
        Instant now = Instant.now();
        Instant from = range.from(now);

        List<User> allUsers = userRepository.findAllWithRoles();
        List<LoginEvent> events = loginEventRepository.findSince(from);

        return new DashboardStats(
                range.name(),
                range.bucketLabel(),
                range.isAll() ? earliest(allUsers, events) : from,
                now,
                totals(allUsers, events, from, now),
                signupSeries(allUsers, range, from, now),
                loginSeries(events, range, from, now),
                lastLoginBuckets(allUsers, now),
                byClient(events),
                byCountry(events),
                recentLogins(),
                geoIpResolver.isDatabaseAvailable());
    }

    private Instant earliest(List<User> users, List<LoginEvent> events) {
        Instant earliest = users.stream()
                .map(User::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(Instant.now());
        return events.stream().map(LoginEvent::getOccurredAt).min(Comparator.naturalOrder())
                .filter(e -> e.isBefore(earliest))
                .orElse(earliest);
    }

    private DashboardStats.Totals totals(List<User> users, List<LoginEvent> events, Instant from, Instant to) {
        long newUsers = users.stream()
                .filter(u -> u.getCreatedAt() != null && !u.getCreatedAt().isBefore(from))
                .count();

        Set<String> ips = events.stream()
                .map(LoginEvent::getIpAddress)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> countries = events.stream()
                .map(LoginEvent::getCountryCode)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        return new DashboardStats.Totals(
                users.size(),
                newUsers,
                users.stream().filter(User::isEnabled).count(),
                users.stream().filter(User::isAccountLocked).count(),
                oAuthClientRepository.count(),
                events.stream().filter(LoginEvent::isSuccessful).count(),
                events.stream().filter(e -> !e.isSuccessful()).count(),
                ips.size(),
                countries.size());
    }

    private List<DashboardStats.TimePoint> signupSeries(
            List<User> users, StatsRange range, Instant from, Instant to) {
        Map<String, Long> counts = new TreeMap<>();
        for (User user : users) {
            if (user.getCreatedAt() == null || user.getCreatedAt().isBefore(from)) {
                continue;
            }
            counts.merge(bucketKey(user.getCreatedAt(), range), 1L, Long::sum);
        }
        return fillBuckets(range, from, to, counts).entrySet().stream()
                .map(e -> new DashboardStats.TimePoint(e.getKey(), e.getValue()))
                .toList();
    }

    private List<DashboardStats.LoginPoint> loginSeries(
            List<LoginEvent> events, StatsRange range, Instant from, Instant to) {
        Map<String, long[]> counts = new TreeMap<>();
        for (LoginEvent event : events) {
            long[] pair = counts.computeIfAbsent(bucketKey(event.getOccurredAt(), range), k -> new long[2]);
            if (event.isSuccessful()) {
                pair[0]++;
            } else {
                pair[1]++;
            }
        }

        // Zero-fill so a quiet period reads as a flat line rather than the chart silently closing
        // the gap and implying activity that never happened.
        List<DashboardStats.LoginPoint> series = new ArrayList<>();
        for (String key : fillBuckets(range, from, to, Map.of()).keySet()) {
            long[] pair = counts.getOrDefault(key, new long[2]);
            series.add(new DashboardStats.LoginPoint(key, pair[0], pair[1]));
        }
        return series;
    }

    /** Produces every bucket in the window, seeded from {@code counts}, so gaps stay visible. */
    private Map<String, Long> fillBuckets(
            StatsRange range, Instant from, Instant to, Map<String, Long> counts) {
        Map<String, Long> filled = new LinkedHashMap<>();
        ChronoUnit unit = range.bucket();

        Instant cursor = truncate(from, unit);
        Instant end = to;
        int guard = 0;
        while (!cursor.isAfter(end) && guard++ < 2000) {
            String key = bucketKey(cursor, range);
            filled.putIfAbsent(key, counts.getOrDefault(key, 0L));
            cursor = plus(cursor, unit);
        }
        // Anything outside the generated grid (clock skew, ALL-range history) still gets in.
        counts.forEach(filled::putIfAbsent);
        return filled;
    }

    private static Instant truncate(Instant instant, ChronoUnit unit) {
        LocalDateTime local = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        return switch (unit) {
            case HOURS -> local.truncatedTo(ChronoUnit.HOURS).toInstant(ZoneOffset.UTC);
            case DAYS -> local.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
            default -> local.toLocalDate().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        };
    }

    private static Instant plus(Instant instant, ChronoUnit unit) {
        // MONTHS cannot be added to an Instant directly — it is not a supported unit there.
        if (unit == ChronoUnit.MONTHS) {
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).plusMonths(1).toInstant(ZoneOffset.UTC);
        }
        return instant.plus(1, unit);
    }

    private static String bucketKey(Instant instant, StatsRange range) {
        LocalDateTime local = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        return switch (range.bucket()) {
            case HOURS -> HOUR.format(local);
            case DAYS -> DAY.format(local);
            default -> MONTH.format(local);
        };
    }

    /**
     * Recency of last sign-in across all users. Ordered oldest-to-newest with "Never" last, since
     * "Never" is the bucket an operator is usually hunting for — dormant or unused accounts.
     */
    private List<DashboardStats.LabelCount> lastLoginBuckets(List<User> users, Instant now) {
        long today = 0, week = 0, month = 0, older = 0, never = 0;
        LocalDate todayDate = LocalDate.ofInstant(now, ZoneOffset.UTC);

        for (User user : users) {
            Instant last = user.getLastLoginAt();
            if (last == null) {
                never++;
                continue;
            }
            long days = ChronoUnit.DAYS.between(LocalDate.ofInstant(last, ZoneOffset.UTC), todayDate);
            if (days <= 0) {
                today++;
            } else if (days <= 7) {
                week++;
            } else if (days <= 30) {
                month++;
            } else {
                older++;
            }
        }

        return List.of(
                new DashboardStats.LabelCount("Today", today),
                new DashboardStats.LabelCount("This week", week),
                new DashboardStats.LabelCount("This month", month),
                new DashboardStats.LabelCount("Over 30 days", older),
                new DashboardStats.LabelCount("Never", never));
    }

    private List<DashboardStats.ClientStat> byClient(List<LoginEvent> events) {
        Map<String, String> names = oAuthClientRepository.findAll().stream()
                .collect(Collectors.toMap(c -> c.getClientId(), c -> c.getClientName(), (a, b) -> a));

        Map<String, List<LoginEvent>> grouped = events.stream()
                .filter(LoginEvent::isSuccessful)
                .collect(Collectors.groupingBy(
                        e -> e.getClientId() == null ? "__console__" : e.getClientId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<DashboardStats.ClientStat> stats = grouped.entrySet().stream()
                .map(entry -> {
                    boolean console = "__console__".equals(entry.getKey());
                    Set<String> users = entry.getValue().stream().map(LoginEvent::getEmail).collect(Collectors.toSet());
                    return new DashboardStats.ClientStat(
                            console ? null : entry.getKey(),
                            // Sign-ins straight into the console aren't attributable to a
                            // registered application, but hiding them would under-report activity.
                            console ? "Admin console" : names.getOrDefault(entry.getKey(), entry.getKey()),
                            entry.getValue().size(),
                            users.size());
                })
                .sorted(Comparator.comparingLong(DashboardStats.ClientStat::logins).reversed())
                .collect(Collectors.toCollection(ArrayList::new));

        return foldTail(stats);
    }

    /** Keeps the chart readable: the tail beyond the slice cap becomes a single "Other" row. */
    private List<DashboardStats.ClientStat> foldTail(List<DashboardStats.ClientStat> stats) {
        if (stats.size() <= MAX_CLIENT_SLICES) {
            return stats;
        }
        List<DashboardStats.ClientStat> head = new ArrayList<>(stats.subList(0, MAX_CLIENT_SLICES - 1));
        List<DashboardStats.ClientStat> tail = stats.subList(MAX_CLIENT_SLICES - 1, stats.size());
        head.add(new DashboardStats.ClientStat(
                null,
                "Other (" + tail.size() + ")",
                tail.stream().mapToLong(DashboardStats.ClientStat::logins).sum(),
                tail.stream().mapToLong(DashboardStats.ClientStat::uniqueUsers).sum()));
        return head;
    }

    private List<DashboardStats.CountryStat> byCountry(List<LoginEvent> events) {
        Map<String, DashboardStats.CountryStat> byKey = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, String> codes = new LinkedHashMap<>();

        for (LoginEvent event : events) {
            if (!event.isSuccessful()) {
                continue;
            }
            String name = event.getCountryName() == null ? "Unknown" : event.getCountryName();
            counts.merge(name, 1L, Long::sum);
            codes.putIfAbsent(name, event.getCountryCode());
        }

        counts.forEach((name, count) -> byKey.put(name, new DashboardStats.CountryStat(codes.get(name), name, count)));

        return byKey.values().stream()
                .sorted(Comparator.comparingLong(DashboardStats.CountryStat::logins).reversed())
                .toList();
    }

    private List<DashboardStats.RecentLogin> recentLogins() {
        return loginEventRepository.findRecent(PageRequest.of(0, RECENT_LOGIN_LIMIT)).stream()
                .map(e -> new DashboardStats.RecentLogin(
                        e.getEmail(),
                        e.isSuccessful(),
                        e.getIpAddress(),
                        e.getCountryCode(),
                        e.getCountryName(),
                        e.getClientId(),
                        e.getOccurredAt()))
                .toList();
    }

    /** Flat CSV of the login events in the window — the dashboard's "Export" action. */
    @Transactional(readOnly = true)
    public String exportCsv(StatsRange range) {
        StringBuilder csv = new StringBuilder("occurred_at,email,successful,ip_address,country_code,country_name,client_id,user_agent\n");
        for (LoginEvent e : loginEventRepository.findSince(range.from(Instant.now()))) {
            csv.append(csvCell(e.getOccurredAt() == null ? "" : e.getOccurredAt().toString())).append(',')
                    .append(csvCell(e.getEmail())).append(',')
                    .append(e.isSuccessful()).append(',')
                    .append(csvCell(e.getIpAddress())).append(',')
                    .append(csvCell(e.getCountryCode())).append(',')
                    .append(csvCell(e.getCountryName())).append(',')
                    .append(csvCell(e.getClientId())).append(',')
                    .append(csvCell(e.getUserAgent())).append('\n');
        }
        return csv.toString();
    }

    /**
     * Quotes every cell and doubles embedded quotes. A user agent contains commas as a matter of
     * course, so unquoted output would silently shift columns.
     */
    private static String csvCell(String value) {
        if (value == null) {
            return "\"\"";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
