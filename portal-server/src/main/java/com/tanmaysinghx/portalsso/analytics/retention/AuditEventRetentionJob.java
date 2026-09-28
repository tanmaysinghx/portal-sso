package com.tanmaysinghx.portalsso.analytics.retention;

import com.tanmaysinghx.portalsso.audit.repository.AuditEventRepository;
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
 * Deletes audit events past the configured retention window.
 *
 * <p>Deletes in batches, each committed separately, rather than one statement for the whole backlog.
 */
@Component
public class AuditEventRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditEventRetentionJob.class);

    /** Bounds a single run in case rows arrive faster than they age out. */
    private static final int MAX_BATCHES_PER_RUN = 500;

    private final AuditEventRepository repository;
    private final RetentionProperties properties;
    private final TransactionTemplate transactionTemplate;

    public AuditEventRetentionJob(
            AuditEventRepository repository,
            RetentionProperties properties,
            TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(cron = "${app.analytics.retention.cron:0 30 3 * * *}")
    public void purgeExpiredAuditEvents() {
        if (!properties.isAuditEventRetentionEnabled()) {
            return;
        }
        run();
    }

    public int run() {
        if (!properties.isAuditEventRetentionEnabled()) {
            return 0;
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.auditEventsDays()));
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
                    "Retention: deleted {} audit event(s) older than {} days.",
                    total,
                    properties.auditEventsDays());
        }
        return total;
    }
}
