package com.tanmaysinghx.portalsso.registration.service;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.registration.config.RegistrationProperties;
import com.tanmaysinghx.portalsso.registration.entity.RegistrationSettings;
import com.tanmaysinghx.portalsso.registration.repository.RegistrationSettingsRepository;
import com.tanmaysinghx.portalsso.registration.web.dto.RegistrationSettingsResponse;
import com.tanmaysinghx.portalsso.registration.web.dto.UpdateRegistrationSettingsRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistent admin-managed registration policy, initialized from deployment defaults until saved. */
@Service
public class RegistrationSettingsService {
    private final RegistrationSettingsRepository repository;
    private final RegistrationProperties defaults;
    private final AuditService auditService;

    public RegistrationSettingsService(
            RegistrationSettingsRepository repository, RegistrationProperties defaults, AuditService auditService) {
        this.repository = repository;
        this.defaults = defaults;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public RegistrationSettingsResponse currentSettings() {
        return repository.findByConfigKey(RegistrationSettings.SINGLETON_KEY)
                .map(settings -> new RegistrationSettingsResponse(settings.isEnabled(), settings.isRequireAdminApproval()))
                .orElseGet(() -> new RegistrationSettingsResponse(defaults.enabled(), defaults.requireAdminApproval()));
    }

    public boolean isEnabled() { return currentSettings().enabled(); }
    public boolean requiresApproval() { return currentSettings().requiresApproval(); }

    @Transactional
    public RegistrationSettingsResponse save(UpdateRegistrationSettingsRequest request) {
        RegistrationSettingsResponse before = currentSettings();
        RegistrationSettings settings = repository.findByConfigKey(RegistrationSettings.SINGLETON_KEY)
                .orElseGet(() -> new RegistrationSettings(before.enabled(), before.requiresApproval()));
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        boolean approval = Boolean.TRUE.equals(request.requiresApproval());
        StringBuilder changes = new StringBuilder();
        if (before.enabled() != enabled) changes.append(enabled ? "public registration enabled" : "public registration disabled");
        if (before.requiresApproval() != approval) {
            if (!changes.isEmpty()) changes.append(", ");
            changes.append(approval ? "admin approval required" : "admin approval not required");
        }
        settings.setEnabled(enabled);
        settings.setRequireAdminApproval(approval);
        settings.setUpdatedBy(currentActor());
        RegistrationSettings saved = repository.save(settings);
        if (!changes.isEmpty()) {
            auditService.record(AuditAction.REGISTRATION_SETTINGS_UPDATED, saved.getId(), "Public registration", changes.toString());
        }
        return new RegistrationSettingsResponse(saved.isEnabled(), saved.isRequireAdminApproval());
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName()) ? null : authentication.getName();
    }
}
