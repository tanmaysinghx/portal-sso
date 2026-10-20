package com.tanmaysinghx.portalsso.security.passwordreset.repository;

import com.tanmaysinghx.portalsso.security.passwordreset.entity.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Caps one mailbox being flooded from many source addresses, which the per-IP limiter cannot. */
    long countByEmailAndCreatedAtAfter(String email, Instant since);

    /**
     * Invalidates every outstanding link for an account: when a new one is issued, and after a
     * reset succeeds. A mailbox should never hold more than one working way to set the password.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken t set t.consumedAt = :now where t.userId = :userId and t.consumedAt is null")
    int consumeAllOutstandingFor(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
