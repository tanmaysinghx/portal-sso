package com.tanmaysinghx.portalsso.audit.web.dto;

import com.tanmaysinghx.portalsso.audit.entity.AuditEvent;
import java.time.Instant;

public record AuditEventResponse(
        String id,
        String actorEmail,
        String action,
        /** The action's display label, so the console does not re-implement the enum's wording. */
        String actionLabel,
        String targetType,
        String targetId,
        String targetLabel,
        String details,
        String ipAddress,
        String userAgent,
        Instant occurredAt) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId().toString(),
                event.getActorEmail(),
                event.getAction().name(),
                event.getAction().getLabel(),
                event.getTargetType().name(),
                event.getTargetId(),
                event.getTargetLabel(),
                event.getDetails(),
                event.getIpAddress(),
                event.getUserAgent(),
                event.getOccurredAt());
    }
}
