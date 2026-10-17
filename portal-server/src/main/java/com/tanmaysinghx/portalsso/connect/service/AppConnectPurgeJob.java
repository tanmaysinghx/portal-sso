package com.tanmaysinghx.portalsso.connect.service;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes connection requests a week after they expire. Submission is unauthenticated, so without
 * this the table would only ever grow; the week keeps recent outcomes visible for troubleshooting.
 * Decisions themselves live on in the audit log.
 */
@Component
public class AppConnectPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(AppConnectPurgeJob.class);
    private static final Duration KEEP_AFTER_EXPIRY = Duration.ofDays(7);

    private final AppConnectService connectService;

    public AppConnectPurgeJob(AppConnectService connectService) {
        this.connectService = connectService;
    }

    @Scheduled(cron = "${app.connect.purge-cron:0 45 * * * *}")
    public void purge() {
        int deleted = connectService.purgeExpired(Instant.now().minus(KEEP_AFTER_EXPIRY));
        if (deleted > 0) {
            log.info("Purged {} expired app connection request(s)", deleted);
        }
    }
}
