package com.tanmaysinghx.portalsso.security.magiclink.retention;

import com.tanmaysinghx.portalsso.security.magiclink.service.MagicLinkService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes magic-link tokens once they are past their retention tail.
 *
 * <p>An expired or consumed token is inert, so this is housekeeping rather than a security control
 * — but the table would otherwise grow without bound on a server where passwordless sign-in is the
 * normal route, one row per sign-in forever. The tail itself is the useful part: it is what lets an
 * administrator answer "was a link issued for this address last week?" after an incident report.
 *
 * <p>Hourly, like the login-event purge, and for the same reason: frequent enough that a single run
 * is never large, which keeps it off the long-lock path.
 */
@Component
public class MagicLinkRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkRetentionJob.class);

    private final MagicLinkService magicLinkService;

    public MagicLinkRetentionJob(MagicLinkService magicLinkService) {
        this.magicLinkService = magicLinkService;
    }

    @Scheduled(cron = "${app.magic-link.purge-cron:0 20 * * * *}")
    public void purge() {
        try {
            int deleted = magicLinkService.purgeExpired(Instant.now());
            if (deleted > 0) {
                log.info("Purged {} expired magic-link token(s)", deleted);
            }
        } catch (Exception e) {
            // Housekeeping must never take the scheduler down with it; the next run retries.
            log.warn("Magic-link purge failed: {}", e.getMessage());
        }
    }
}
