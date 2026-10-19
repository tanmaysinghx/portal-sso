package com.tanmaysinghx.portalsso.security.magiclink.entity;

import com.tanmaysinghx.portalsso.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One issued magic link.
 *
 * <p>The token that was emailed is <strong>not</strong> stored — only its SHA-256. A magic link is
 * a bearer credential that signs someone in on sight, so storing it would make a database dump a
 * stack of working sign-in links, exactly the way storing passwords in the clear would. Hashing is
 * enough here without a slow KDF: the token is 256 bits of {@code SecureRandom}, so there is no
 * guessable input to brute-force the way there is with a human-chosen password.
 *
 * <p>Single use. {@link #consumedAt} is set the moment a link is redeemed, because the link lives
 * on in the mailbox — and, less obviously, in the logs of every mail gateway and link scanner it
 * passed through on the way.
 */
@Entity
@Table(name = "magic_link_tokens")
public class MagicLinkToken extends BaseEntity {

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private MagicLinkPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "requester_ip", length = 45)
    private String requesterIp;

    protected MagicLinkToken() {
        // JPA
    }

    public MagicLinkToken(
            String tokenHash, String email, MagicLinkPurpose purpose, Instant expiresAt, String requesterIp) {
        this.tokenHash = tokenHash;
        this.email = email;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.requesterIp = requesterIp;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getEmail() {
        return email;
    }

    public MagicLinkPurpose getPurpose() {
        return purpose;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public String getRequesterIp() {
        return requesterIp;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    /** Redeemable exactly once, and only before it expires. */
    public boolean isRedeemable(Instant now) {
        return !isConsumed() && !isExpired(now);
    }
}
