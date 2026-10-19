package com.tanmaysinghx.portalsso.analytics.retention.web.dto;

/** Retention periods in hours; zero means records are kept indefinitely. */
public record RetentionSettingsResponse(int loginEventsHours, int auditEventsHours) {}
