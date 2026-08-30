package com.tanmaysinghx.portalsso.audit.entity;

import com.tanmaysinghx.portalsso.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One recorded administrative change: who did what, to which object, from where.
 *
 * <p>Append-only. Nothing in the application updates or deletes a row, and there is deliberately no
 * endpoint that can — an audit trail an administrator can edit answers no question worth asking.
 * There are no setters for the same reason: every field is fixed at construction.
 *
 * <p>{@link #details} holds a short human-readable summary of what changed. It must never carry a
 * credential: passwords, TOTP secrets and recovery codes are named but never valued here, since
 * this table is read by more people than the {@code users} table is.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent extends BaseEntity {

    /** Null when the action had no authenticated actor, as with self-registration. */
    @Column(name = "actor_id", length = 36)
    private String actorId;

    @Column(name = "actor_email", nullable = false, length = 255)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private AuditAction.TargetType targetType;

    @Column(name = "target_id", length = 36)
    private String targetId;

    /** Captured at the time, so a deleted target is still identifiable afterwards. */
    @Column(name = "target_label", length = 255)
    private String targetLabel;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
        // JPA
    }

    public AuditEvent(
            String actorId,
            String actorEmail,
            AuditAction action,
            String targetId,
            String targetLabel,
            String details,
            String ipAddress,
            String userAgent,
            Instant occurredAt) {
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.action = action;
        // Derived from the action rather than passed in, so the two can never disagree.
        this.targetType = action.getTargetType();
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.details = details;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.occurredAt = occurredAt;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditAction.TargetType getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public String getDetails() {
        return details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
