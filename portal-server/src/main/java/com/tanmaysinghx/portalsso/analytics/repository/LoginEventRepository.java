package com.tanmaysinghx.portalsso.analytics.repository;

import com.tanmaysinghx.portalsso.analytics.entity.LoginEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {

    /**
     * Rows are fetched for the window and bucketed in Java rather than grouped with SQL date
     * functions. Those functions differ between MySQL and H2 (and Postgres), and this codebase has
     * already been bitten twice by dialect divergence — a query that groups correctly on the test
     * database and wrongly in production is exactly the failure mode worth avoiding here.
     */
    @Query("select e from LoginEvent e where e.occurredAt >= :from order by e.occurredAt asc")
    List<LoginEvent> findSince(@Param("from") Instant from);

    @Query("select e from LoginEvent e order by e.occurredAt desc")
    List<LoginEvent> findRecent(Pageable pageable);

    /**
     * Ids of events older than the cutoff, oldest first, for the retention job to delete by id.
     *
     * <p>Selecting ids and deleting them separately rather than {@code DELETE … LIMIT}: MySQL
     * supports that clause, PostgreSQL does not, and a portable extra query is cheaper than a
     * dialect-specific one this project would then have to test twice.
     */
    @Query("select e.id from LoginEvent e where e.occurredAt < :cutoff order by e.occurredAt asc")
    List<UUID> findIdsOlderThan(@Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Bounded variant of {@link #findSince}. The dashboard buckets a window in memory, which is the
     * right trade at this size but unbounded by nature — without a cap, "All time" on a mature
     * deployment would try to load every sign-in ever recorded into one list.
     */
    @Query("select e from LoginEvent e where e.occurredAt >= :from order by e.occurredAt asc")
    List<LoginEvent> findSinceBounded(@Param("from") Instant from, Pageable pageable);

    long countByOccurredAtGreaterThanEqual(Instant from);

    long countBySuccessfulAndOccurredAtGreaterThanEqual(boolean successful, Instant from);
}
