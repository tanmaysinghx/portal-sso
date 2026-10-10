package com.tanmaysinghx.portalsso.application.web.dto;

import com.tanmaysinghx.portalsso.application.entity.AccessRequest;
import com.tanmaysinghx.portalsso.application.entity.AccessRequestStatus;
import java.time.Instant;
import java.util.UUID;

public record AccessRequestResponse(
        UUID id,
        UserSummary user,
        AppSummary application,
        UserSummary reviewer,
        AccessRequestStatus status,
        String reason,
        String reviewerComment,
        Instant resolvedAt,
        Instant createdAt) {

    public record UserSummary(UUID id, String email, String firstName, String lastName) {}
    public record AppSummary(UUID id, String name, String category, String iconUrl) {}

    public static AccessRequestResponse from(AccessRequest ar) {
        return new AccessRequestResponse(
                ar.getId(),
                new UserSummary(ar.getUser().getId(), ar.getUser().getEmail(),
                        ar.getUser().getFirstName(), ar.getUser().getLastName()),
                new AppSummary(ar.getApplication().getId(), ar.getApplication().getName(),
                        ar.getApplication().getCategory(), ar.getApplication().getIconUrl()),
                new UserSummary(ar.getReviewer().getId(), ar.getReviewer().getEmail(),
                        ar.getReviewer().getFirstName(), ar.getReviewer().getLastName()),
                ar.getStatus(),
                ar.getReason(),
                ar.getReviewerComment(),
                ar.getResolvedAt(),
                ar.getCreatedAt());
    }
}
