package com.tanmaysinghx.portalsso.user.web.dto;

import java.time.Instant;

public record UserSessionDto(
        String id,
        Instant creationTime,
        Instant lastAccessedTime,
        String ipAddress,
        String userAgent,
        boolean isCurrent) {
}
