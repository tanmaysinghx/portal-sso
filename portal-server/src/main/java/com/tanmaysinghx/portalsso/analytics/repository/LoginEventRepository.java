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

    long countByOccurredAtGreaterThanEqual(Instant from);

    long countBySuccessfulAndOccurredAtGreaterThanEqual(boolean successful, Instant from);
}
