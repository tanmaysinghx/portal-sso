package com.tanmaysinghx.portalsso.user.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record UserSessionDto(
        String id,
        Instant creationTime,
        Instant lastAccessedTime,
        String ipAddress,
        String userAgent,
        @JsonProperty("isCurrent") boolean isCurrent) {

    @JsonProperty("currentSession")
    public boolean currentSession() {
        return isCurrent;
    }
}
