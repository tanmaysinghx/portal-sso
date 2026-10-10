package com.tanmaysinghx.portalsso.application.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SubmitAccessRequestRequest(
        @NotNull(message = "Application ID is required") UUID applicationId,
        @NotNull(message = "Reviewer ID is required") UUID reviewerId,
        @Size(max = 500, message = "Reason cannot exceed 500 characters") String reason) {}
