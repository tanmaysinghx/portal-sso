package com.tanmaysinghx.portalsso.security.passwordreset.retention;

import com.tanmaysinghx.portalsso.security.passwordreset.service.PasswordResetService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly housekeeping for spent reset links, mirroring {@code MagicLinkRetentionJob}. */
@Component
public class PasswordResetRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetRetentionJob.class);

    private final PasswordResetService passwordResetService;

    public PasswordResetRetentionJob(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Scheduled(cron = "${app.password-reset.purge-cron:0 25 * * * *}")
    public void purge() {
        try {
            int deleted = passwordResetService.purgeExpired(Instant.now());
            if (deleted > 0) {
                log.info("Purged {} expired password-reset token(s)", deleted);
            }
        } catch (Exception e) {
            log.warn("Password-reset purge failed: {}", e.getMessage());
        }
    }
}
