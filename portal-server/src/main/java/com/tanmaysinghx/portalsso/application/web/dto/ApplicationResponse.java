package com.tanmaysinghx.portalsso.application.web.dto;

import com.tanmaysinghx.portalsso.application.entity.Application;
import com.tanmaysinghx.portalsso.application.entity.ApplicationAccessType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        String name,
        String description,
        String appUrl,
        String iconUrl,
        String category,
        String clientId,
        ApplicationAccessType accessType,
        List<RoleSummary> roles,
        boolean enabled,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt) {

    public record RoleSummary(UUID id, String name, String description) {}

    public static ApplicationResponse from(Application app) {
        List<RoleSummary> roleSummaries = app.getRoles() == null
                ? List.of()
                : app.getRoles().stream()
                        .map(r -> new RoleSummary(r.getId(), r.getName(), r.getDescription()))
                        .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                        .toList();

        return new ApplicationResponse(
                app.getId(),
                app.getName(),
                app.getDescription(),
                app.getAppUrl(),
                app.getIconUrl(),
                app.getCategory() == null ? "General" : app.getCategory(),
                app.getClientId(),
                app.getAccessType() == null ? ApplicationAccessType.ALL_USERS : app.getAccessType(),
                roleSummaries,
                app.isEnabled(),
                app.getDisplayOrder(),
                app.getCreatedAt(),
                app.getUpdatedAt());
    }
}
