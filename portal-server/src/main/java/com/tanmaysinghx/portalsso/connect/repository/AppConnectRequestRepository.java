package com.tanmaysinghx.portalsso.connect.repository;

import com.tanmaysinghx.portalsso.connect.entity.AppConnectRequest;
import com.tanmaysinghx.portalsso.connect.entity.AppConnectStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppConnectRequestRepository extends JpaRepository<AppConnectRequest, UUID> {

    long countByStatusAndExpiresAtAfter(AppConnectStatus status, Instant now);

    List<AppConnectRequest> findByStatusAndExpiresAtAfterOrderByCreatedAtDesc(AppConnectStatus status, Instant now);

    /**
     * Compare-and-set from APPROVED to REDEEMED. Returns 1 for exactly one caller, so two concurrent
     * redeems with the right verifier cannot both receive freshly generated credentials.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AppConnectRequest r SET r.status = com.tanmaysinghx.portalsso.connect.entity.AppConnectStatus.REDEEMED, "
            + "r.redeemedAt = :now WHERE r.id = :id AND r.status = com.tanmaysinghx.portalsso.connect.entity.AppConnectStatus.APPROVED "
            + "AND r.expiresAt > :now")
    int markRedeemed(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM AppConnectRequest r WHERE r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
