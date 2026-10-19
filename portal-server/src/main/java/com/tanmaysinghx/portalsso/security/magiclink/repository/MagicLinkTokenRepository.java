package com.tanmaysinghx.portalsso.security.magiclink.repository;

import com.tanmaysinghx.portalsso.security.magiclink.entity.MagicLinkToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, UUID> {

    /** The only lookup there is: a redeemer presents a token, never an id. */
    Optional<MagicLinkToken> findByTokenHash(String tokenHash);

    /**
     * How many links this address has been sent recently. Caps a mailbox being used as a
     * flood target by someone who only needs to know the address exists, which the per-IP rate
     * limiter cannot do on its own — that one is defeated by rotating source addresses.
     */
    long countByEmailAndCreatedAtAfter(String email, Instant since);

    /**
     * Invalidates every outstanding link for an address.
     *
     * <p>Called when a new one is issued and after a successful redeem. Both matter: leaving older
     * links live means a user who clicks "send me a link" three times has three working
     * credentials in their mailbox, and the oldest is the one most likely to have leaked.
     */
    //
    // flushAutomatically/clearAutomatically because this is a bulk JPQL update, which goes straight
    // to the database and leaves any already-loaded MagicLinkToken in the persistence context
    // showing its old consumedAt. Without the clear, a read later in the same transaction sees
    // stale rows that look outstanding when they are not.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MagicLinkToken t set t.consumedAt = :now "
            + "where t.email = :email and t.consumedAt is null")
    int consumeAllOutstandingFor(@Param("email") String email, @Param("now") Instant now);

    /** Retention: a redeemed or expired token has no further use, and the row is a credential record. */
    @Modifying
    @Query("delete from MagicLinkToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
