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
        String clientId) {

    public static UserApplicationResponse from(Application app) {
        return new UserApplicationResponse(
                app.getId(),
                app.getName(),
                app.getDescription(),
                app.getAppUrl(),
                app.getIconUrl(),
                app.getCategory() == null ? "General" : app.getCategory(),
                app.getClientId());
    }
}
