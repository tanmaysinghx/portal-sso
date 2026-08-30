package com.tanmaysinghx.portalsso.analytics.web;

import com.tanmaysinghx.portalsso.analytics.service.DashboardStatsService;
import com.tanmaysinghx.portalsso.analytics.service.StatsRange;
import com.tanmaysinghx.portalsso.analytics.web.dto.DashboardStats;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stats")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private final DashboardStatsService statsService;

    public AdminStatsController(DashboardStatsService statsService) {
        this.statsService = statsService;
    }

    /** One request backs the whole dashboard, so switching the time filter is a single round trip. */
    @GetMapping
    public DashboardStats stats(@RequestParam(name = "range", required = false) String range) {
        return statsService.build(StatsRange.parse(range));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(@RequestParam(name = "range", required = false) String range) {
        StatsRange parsed = StatsRange.parse(range);
        String filename = "portal-sso-logins-%s-%s.csv".formatted(parsed.name().toLowerCase(), LocalDate.now());

        return ResponseEntity.ok()
                // attachment + filename so the browser saves it rather than rendering it inline.
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(statsService.exportCsv(parsed));
    }
}
