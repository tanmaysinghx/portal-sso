package com.tanmaysinghx.portalsso.application.web.dto;

import com.tanmaysinghx.portalsso.application.entity.Application;
import java.util.UUID;

public record UserApplicationResponse(
        UUID id,
        String name,
        String description,
        String appUrl,
        String iconUrl,
        String category,
        String clientId,
        boolean accessible,
        String pendingRequestStatus) {

    public static UserApplicationResponse from(Application app, boolean accessible, String pendingRequestStatus) {
        return new UserApplicationResponse(
                app.getId(),
                app.getName(),
                app.getDescription(),
                app.getAppUrl(),
                app.getIconUrl(),
                app.getCategory() == null ? "General" : app.getCategory(),
                app.getClientId(),
                accessible,
                pendingRequestStatus);
    }
}
