package com.tanmaysinghx.portalsso.analytics.retention.service;

import com.tanmaysinghx.portalsso.analytics.retention.RetentionProperties;
import com.tanmaysinghx.portalsso.analytics.retention.entity.AnalyticsRetentionSettings;
import com.tanmaysinghx.portalsso.analytics.retention.repository.AnalyticsRetentionSettingsRepository;
import com.tanmaysinghx.portalsso.analytics.retention.web.dto.RetentionSettingsResponse;
import com.tanmaysinghx.portalsso.analytics.retention.web.dto.UpdateRetentionSettingsRequest;
import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistent, admin-managed retention settings, with deployment properties as first-run defaults. */
@Service
public class AnalyticsRetentionSettingsService {

    private final AnalyticsRetentionSettingsRepository repository;
    private final RetentionProperties properties;
    private final AuditService auditService;

    public AnalyticsRetentionSettingsService(
            AnalyticsRetentionSettingsRepository repository,
            RetentionProperties properties,
            AuditService auditService) {
        this.repository = repository;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public RetentionSettingsResponse currentSettings() {
        return repository.findByConfigKey(AnalyticsRetentionSettings.SINGLETON_KEY)
                .map(settings -> new RetentionSettingsResponse(
                        settings.getLoginEventsHours(), settings.getAuditEventsHours()))
                .orElseGet(() -> new RetentionSettingsResponse(
                        configuredHours(properties.loginEventsHours(), properties.loginEventsDays()),
                        configuredHours(properties.auditEventsHours(), properties.auditEventsDays())));
    }

    @Transactional
    public RetentionSettingsResponse save(UpdateRetentionSettingsRequest request) {
        RetentionSettingsResponse before = currentSettings();
        AnalyticsRetentionSettings settings = repository.findByConfigKey(AnalyticsRetentionSettings.SINGLETON_KEY)
                .orElseGet(() -> new AnalyticsRetentionSettings(
                        before.loginEventsHours(), before.auditEventsHours()));

        List<String> changedFields = new ArrayList<>();
        if (before.loginEventsHours() != request.loginEventsHours()) {
            changedFields.add("login-events retention");
        }
        if (before.auditEventsHours() != request.auditEventsHours()) {
            changedFields.add("audit-events retention");
        }

        settings.setLoginEventsHours(request.loginEventsHours());
        settings.setAuditEventsHours(request.auditEventsHours());
        settings.setUpdatedBy(currentActor());
        AnalyticsRetentionSettings saved = repository.save(settings);

        if (!changedFields.isEmpty()) {
            auditService.record(
                    AuditAction.ANALYTICS_RETENTION_UPDATED,
                    saved.getId(),
                    "Analytics retention",
                    String.join(", ", changedFields));
        }
        return new RetentionSettingsResponse(saved.getLoginEventsHours(), saved.getAuditEventsHours());
    }

    private static int configuredHours(Integer hours, Integer days) {
        if (hours != null && hours > 0) return hours;
        if (days != null && days > 0) return Math.multiplyExact(days, 24);
        return 0;
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())
                ? null
                : authentication.getName();
    }
}
