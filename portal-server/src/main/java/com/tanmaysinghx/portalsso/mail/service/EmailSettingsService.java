package com.tanmaysinghx.portalsso.mail.service;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.mail.entity.EmailSettings;
import com.tanmaysinghx.portalsso.mail.entity.EmailTemplateType;
import com.tanmaysinghx.portalsso.mail.repository.EmailSettingsRepository;
import com.tanmaysinghx.portalsso.mail.web.dto.EmailSettingsResponse;
import com.tanmaysinghx.portalsso.mail.web.dto.UpdateEmailSettingsRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and stores branding, the per-notification switches, and magic-link policy. */
@Service
public class EmailSettingsService {

    private final EmailSettingsRepository repository;
    private final SmtpSettingsService smtpSettingsService;
    private final AuditService auditService;
    private final String issuerUrl;

    public EmailSettingsService(
            EmailSettingsRepository repository,
            SmtpSettingsService smtpSettingsService,
            AuditService auditService,
            @Value("${spring.security.oauth2.authorizationserver.issuer:http://localhost:8080}") String issuerUrl) {
        this.repository = repository;
        this.smtpSettingsService = smtpSettingsService;
        this.auditService = auditService;
        this.issuerUrl = issuerUrl;
    }

    /**
     * The stored settings, or an unsaved default when nothing has been configured.
     *
     * <p>A transient instance rather than a created row, for the same reason as
     * {@code SmtpSettingsService.currentSettings()}: this is read on the sign-in path, and a server
     * where nobody opened the email tab should not grow a row as a side effect.
     */
    @Transactional(readOnly = true)
    public EmailSettings currentSettings() {
        return repository.findByConfigKey(EmailSettings.SINGLETON_KEY).orElseGet(EmailSettings::new);
    }

    @Transactional(readOnly = true)
    public EmailBranding currentBranding() {
        return EmailBranding.from(currentSettings(), issuerUrl);
    }

    @Transactional(readOnly = true)
    public EmailSettingsResponse getSettings() {
        return toResponse(currentSettings());
    }

    @Transactional
    public EmailSettingsResponse save(UpdateEmailSettingsRequest request) {
        EmailSettings settings = repository.findByConfigKey(EmailSettings.SINGLETON_KEY)
                .orElseGet(EmailSettings::new);

        List<String> changes = describeChanges(settings, request);

        settings.setProductName(trimToNull(request.productName()));
        settings.setLogoUrl(trimToNull(request.logoUrl()));
        settings.setAccentColor(trimToNull(request.accentColor()));
        settings.setSupportUrl(trimToNull(request.supportUrl()));
        settings.setFooterNote(trimToNull(request.footerNote()));
        settings.setWelcomeEnabled(Boolean.TRUE.equals(request.welcomeEnabled()));
        settings.setNewDeviceEnabled(Boolean.TRUE.equals(request.newDeviceEnabled()));
        settings.setSignInNoticeEnabled(Boolean.TRUE.equals(request.signInNoticeEnabled()));
        settings.setMagicLinkEnabled(Boolean.TRUE.equals(request.magicLinkEnabled()));
        settings.setMagicLinkRegistrationEnabled(Boolean.TRUE.equals(request.magicLinkRegistrationEnabled()));
        settings.setMagicLinkTtlMinutes(request.magicLinkTtlMinutes());
        settings.setUpdatedBy(currentActor());

        EmailSettings saved = repository.save(settings);

        auditService.record(
                AuditAction.EMAIL_SETTINGS_UPDATED,
                saved.getId(),
                EmailBranding.from(saved, issuerUrl).productName(),
                changes.isEmpty() ? "Email settings saved with no changes" : String.join("; ", changes));

        return toResponse(saved);
    }

    /**
     * Whether a notification of this type should be sent at all.
     *
     * <p>Checked before anything is rendered or any socket is opened, so a switched-off
     * notification costs a single indexed read.
     */
    @Transactional(readOnly = true)
    public boolean isNotificationEnabled(EmailTemplateType type) {
        return currentSettings().isEnabled(type);
    }

    private EmailSettingsResponse toResponse(EmailSettings settings) {
        EmailBranding branding = EmailBranding.from(settings, issuerUrl);
        boolean deliveryReady = smtpSettingsService.currentSettings().isUsable();

        List<EmailSettingsResponse.NotificationStatus> notifications =
                Arrays.stream(EmailTemplateType.values())
                        .map(type -> {
                            boolean enabled = settings.isEnabled(type);
                            return new EmailSettingsResponse.NotificationStatus(
                                    type.name(),
                                    type.getLabel(),
                                    type.getDescription(),
                                    enabled,
                                    status(enabled, deliveryReady));
                        })
                        .toList();

        return new EmailSettingsResponse(
                settings.getProductName(),
                settings.getLogoUrl(),
                settings.getAccentColor(),
                settings.getSupportUrl(),
                settings.getFooterNote(),
                settings.isWelcomeEnabled(),
                settings.isNewDeviceEnabled(),
                settings.isSignInNoticeEnabled(),
                settings.isMagicLinkEnabled(),
                settings.isMagicLinkRegistrationEnabled(),
                settings.getMagicLinkTtlMinutes(),
                deliveryReady,
                branding.portalUrl(),
                settings.getUpdatedBy(),
                settings.getUpdatedAt(),
                notifications);
    }

    /** "On" while nothing can be delivered is the state worth naming rather than hiding. */
    private static String status(boolean enabled, boolean deliveryReady) {
        if (!enabled) {
            return "OFF";
        }
        return deliveryReady ? "ON" : "NO_RELAY";
    }

    private static List<String> describeChanges(EmailSettings before, UpdateEmailSettingsRequest after) {
        List<String> changes = new ArrayList<>();
        addIfChanged(changes, "product name", before.getProductName(), trimToNull(after.productName()));
        addIfChanged(changes, "logo", before.getLogoUrl(), trimToNull(after.logoUrl()));
        addIfChanged(changes, "accent colour", before.getAccentColor(), trimToNull(after.accentColor()));
        addIfChanged(changes, "support URL", before.getSupportUrl(), trimToNull(after.supportUrl()));
        addIfChanged(changes, "footer note", before.getFooterNote(), trimToNull(after.footerNote()));
        addToggle(changes, "welcome email", before.isWelcomeEnabled(), after.welcomeEnabled());
        addToggle(changes, "new-device alert", before.isNewDeviceEnabled(), after.newDeviceEnabled());
        addToggle(changes, "sign-in notice", before.isSignInNoticeEnabled(), after.signInNoticeEnabled());
        addToggle(changes, "magic link sign-in", before.isMagicLinkEnabled(), after.magicLinkEnabled());
        addToggle(changes, "magic link registration",
                before.isMagicLinkRegistrationEnabled(), after.magicLinkRegistrationEnabled());
        if (before.getMagicLinkTtlMinutes() != after.magicLinkTtlMinutes()) {
            // The value is recorded here, unlike a credential: how long a sign-in link stays valid
            // is a security parameter, and a reviewer needs to see what it was changed to.
            changes.add("magic link lifetime set to " + after.magicLinkTtlMinutes() + " minutes");
        }
        return changes;
    }

    private static void addIfChanged(List<String> changes, String field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(field + " changed");
        }
    }

    private static void addToggle(List<String> changes, String field, boolean before, Boolean after) {
        boolean now = Boolean.TRUE.equals(after);
        if (before != now) {
            changes.add(field + (now ? " enabled" : " disabled"));
        }
    }

    private static String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
