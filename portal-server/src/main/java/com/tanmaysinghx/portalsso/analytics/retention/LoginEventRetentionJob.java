package com.tanmaysinghx.portalsso.analytics.retention;

import com.tanmaysinghx.portalsso.analytics.repository.LoginEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes sign-in events past the configured retention window.
 *
 * <p>Deletes in batches, each committed separately, rather than one statement for the whole backlog.
 * A first run against a table that has accumulated for a year would otherwise be a single very large
 * delete — on MySQL that means a long lock with sign-ins queued behind it, a poor trade for a
 * housekeeping job. Batching keeps the lock short and the work interruptible.
 *
 * <p>Rows are selected by id and then deleted by id rather than with {@code DELETE … LIMIT}: MySQL
 * supports that clause and PostgreSQL does not, and this codebase has been bitten by dialect
 * divergence often enough to keep the portable form even when it costs an extra query.
 *
 * <p>The loop stops as soon as a batch comes back short, so on the ordinary day when nothing has
 * aged out it does one cheap query and returns.
 */
@Component
public class LoginEventRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(LoginEventRetentionJob.class);

    /** Bounds a single run in case rows arrive faster than they age out. */
    private static final int MAX_BATCHES_PER_RUN = 500;

    private final LoginEventRepository repository;
    private final RetentionProperties properties;

    /**
     * A {@link TransactionTemplate} rather than {@code @Transactional} on a helper method: a method
     * called through {@code this} does not go through the proxy, so the annotation would be silently
     * ignored and every batch would share the caller's transaction — rebuilding exactly the
     * long-running delete the batching exists to avoid.
     */
    private final TransactionTemplate transactionTemplate;

    public LoginEventRetentionJob(
            LoginEventRepository repository,
            RetentionProperties properties,
            TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(cron = "${app.analytics.retention.cron:0 30 3 * * *}")
    public void purgeExpiredLoginEvents() {
        if (!properties.isLoginEventRetentionEnabled()) {
            return;
        }
        run();
    }

    /**
     * @return how many rows were removed. Public and separate from the scheduled entry point so a
     *     test can drive it without waiting for the cron.
     */
    public int run() {
        if (!properties.isLoginEventRetentionEnabled()) {
            return 0;
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.loginEventsDays()));
        int batchSize = properties.batchSize();
        int total = 0;

        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            Integer deleted = transactionTemplate.execute(status -> {
                List<UUID> ids = repository.findIdsOlderThan(cutoff, PageRequest.of(0, batchSize));
                if (ids.isEmpty()) {
                    return 0;
                }
                repository.deleteAllByIdInBatch(ids);
                return ids.size();
            });

            int removed = deleted == null ? 0 : deleted;
            total += removed;
            if (removed < batchSize) {
                break;
            }
        }

        if (total > 0) {
            log.info(
                    "Retention: deleted {} login event(s) older than {} days.",
                    total,
                    properties.loginEventsDays());
        }
        return total;
    }
}
