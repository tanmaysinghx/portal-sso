package com.tanmaysinghx.portalsso.analytics.retention.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Zero keeps records indefinitely; positive values are hours. */
public record UpdateRetentionSettingsRequest(
        @Min(0) @Max(87600) int loginEventsHours,
        @Min(0) @Max(87600) int auditEventsHours) {}
