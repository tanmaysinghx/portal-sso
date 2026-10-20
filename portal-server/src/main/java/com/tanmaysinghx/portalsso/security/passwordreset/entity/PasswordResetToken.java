package com.tanmaysinghx.portalsso.security.passwordreset.entity;

import com.tanmaysinghx.portalsso.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One emailed "reset your password" link. Only the SHA-256 of the token is stored — see
 * {@link com.tanmaysinghx.portalsso.security.token.OpaqueTokens}.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID userId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "requester_ip", length = 45)
    private String requesterIp;

    protected PasswordResetToken() {
        // JPA
    }

    public PasswordResetToken(String tokenHash, UUID userId, String email, Instant expiresAt, String requesterIp) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.email = email;
        this.expiresAt = expiresAt;
        this.requesterIp = requesterIp;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
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
        return !now.isBefore(expiresAt);
    }
}
