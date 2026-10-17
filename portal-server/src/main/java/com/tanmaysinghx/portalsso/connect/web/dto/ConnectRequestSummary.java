package com.tanmaysinghx.portalsso.connect.web.dto;

import com.tanmaysinghx.portalsso.connect.entity.AppConnectRequest;
import java.time.Instant;
import java.util.UUID;

/** A pending request as listed in the admin console. */
public record ConnectRequestSummary(
        UUID id,
        String appName,
        String product,
        String appUrl,
        String requesterIp,
        Instant createdAt,
        Instant expiresAt,
        String reviewUrl) {

    public static ConnectRequestSummary from(AppConnectRequest r) {
        return new ConnectRequestSummary(
                r.getId(), r.getAppName(), r.getProduct(), r.getAppUrl(), r.getRequesterIp(),
                r.getCreatedAt(), r.getExpiresAt(), "/connect/" + r.getId());
    }
}
