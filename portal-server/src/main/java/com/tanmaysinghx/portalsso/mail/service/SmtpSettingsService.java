package com.tanmaysinghx.portalsso.mail.service;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.mail.entity.SmtpSettings;
import com.tanmaysinghx.portalsso.mail.entity.SmtpTransportSecurity;
import com.tanmaysinghx.portalsso.mail.repository.SmtpSettingsRepository;
import com.tanmaysinghx.portalsso.mail.web.dto.SendTestEmailRequest;
import com.tanmaysinghx.portalsso.mail.web.dto.SendTestEmailResponse;
import com.tanmaysinghx.portalsso.mail.web.dto.SmtpSettingsResponse;
import com.tanmaysinghx.portalsso.mail.web.dto.UpdateSmtpSettingsRequest;
import com.tanmaysinghx.portalsso.security.mfa.MfaEncryptionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads, validates and stores the server's SMTP configuration, and proves it works.
 *
 * <h2>Encrypting the relay password</h2>
 *
 * <p>The password is encrypted at rest with {@link MfaEncryptionService}, which despite its name is
 * this deployment's single AES-256-GCM at-rest cipher, keyed by
 * {@code app.security.mfa.encryption-key}. Reusing it is deliberate: a second key would be another
 * thing for an operator to generate, back up and eventually lose, and losing it would break mail
 * for reasons nobody would connect to a key file. The key is auto-generated into
 * {@code ~/.portal-sso/secrets/mfa.key} on first start, so this works without configuration while
 * still never storing the password in the clear.
 *
 * <p>When no key is available at all, saving a password is refused outright rather than stored in
 * plaintext — the same stance {@code MfaEncryptionService} takes on TOTP secrets. Every other
 * field still saves, so an operator can configure a credential-free relay regardless.
 */
@Service
public class SmtpSettingsService {

    private static final Logger log = LoggerFactory.getLogger(SmtpSettingsService.class);

    private static final String TEST_SUBJECT = "Portal SSO test message";
    private static final String TEST_BODY = """
            This is a test message from Portal SSO.

            If you are reading it, the SMTP relay configured in Settings -> Email (SMTP) can \
            deliver mail, and features that email your users will work.

            No action is needed.
            """;

    private final SmtpSettingsRepository repository;
    private final MfaEncryptionService encryptionService;
    private final SmtpMailSender mailSender;
    private final AuditService auditService;

    public SmtpSettingsService(
            SmtpSettingsRepository repository,
            MfaEncryptionService encryptionService,
            SmtpMailSender mailSender,
            AuditService auditService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.mailSender = mailSender;
        this.auditService = auditService;
    }

    /**
     * The stored configuration, or an unsaved, disabled default when nothing has been configured.
     *
     * <p>Returning a transient instance rather than creating the row keeps this a pure read.
     * {@link MailService#isEnabled()} calls it on paths that may run against a read-only
     * connection, and a server where nobody has opened the email settings should not accumulate a
     * row as a side effect of something checking whether mail is on.
     */
    @Transactional(readOnly = true)
    public SmtpSettings currentSettings() {
        return repository.findByConfigKey(SmtpSettings.SINGLETON_KEY).orElseGet(SmtpSettings::new);
    }

    @Transactional(readOnly = true)
    public SmtpSettingsResponse getSettings() {
        return toResponse(currentSettings());
    }

    /**
     * Validates and stores the configuration.
     *
     * <p>A {@code null} password means "leave the stored one alone", which is what the console
     * sends when an administrator changes the port without retyping the secret. An empty string
     * clears it — the way to move to a relay that takes no credentials.
     */
    @Transactional
    public SmtpSettingsResponse save(UpdateSmtpSettingsRequest request) {
        SmtpSettings settings = repository.findByConfigKey(SmtpSettings.SINGLETON_KEY)
                .orElseGet(SmtpSettings::new);

        boolean enabled = Boolean.TRUE.equals(request.enabled());
        if (enabled) {
            // Only checked when switching on: a partially filled configuration is a normal
            // intermediate state while someone is still looking up their provider's settings, and
            // losing it on save would be worse than saving something that cannot yet send.
            requireCompleteForSending(request);
        }

        List<String> changes = describeChanges(settings, request);

        settings.setEnabled(enabled);
        settings.setHost(trimToNull(request.host()));
        settings.setPort(request.port());
        settings.setTransportSecurity(request.transportSecurity());
        settings.setUsername(trimToNull(request.username()));
        settings.setFromAddress(trimToNull(request.fromAddress()));
        settings.setFromName(trimToNull(request.fromName()));
        settings.setReplyToAddress(trimToNull(request.replyToAddress()));
        settings.setUpdatedBy(currentActor());
        applyPassword(settings, request.password());

        SmtpSettings saved = repository.save(settings);

        // Names the fields that changed, never their values — a relay password must not reach the
        // audit log, and host and username are recorded because knowing where mail started being
        // routed is the point of auditing this at all.
        auditService.record(
                AuditAction.SMTP_SETTINGS_UPDATED,
                saved.getId(),
                saved.getHost() == null ? "(no relay configured)" : saved.getHost(),
                changes.isEmpty() ? "SMTP settings saved with no changes" : String.join("; ", changes));

        return toResponse(saved);
    }

    /**
     * Sends one test message.
     *
     * <p>When the request carries settings they are used as given, so a relay can be proved before
     * it is saved or switched on. Only the outcome is written back, and only when a configuration
     * row already exists — testing unsaved values must not create one.
     */
    @Transactional
    public SendTestEmailResponse sendTestEmail(SendTestEmailRequest request) {
        SmtpSettings stored = repository.findByConfigKey(SmtpSettings.SINGLETON_KEY).orElse(null);
        SmtpConnection connection = resolveForTest(request, stored);
        String recipient = request.recipient().strip();

        boolean success;
        String detail;
        try {
            mailSender.send(connection, recipient, TEST_SUBJECT, TEST_BODY);
            success = true;
            detail = null;
        } catch (MailException | SmtpMailSender.MessagingFailure e) {
            log.warn("SMTP test send to {} via {}:{} failed",
                    recipient, connection.host(), connection.port(), e);
            success = false;
            detail = SmtpFailures.describe(e);
        }

        if (stored != null) {
            stored.setLastTestAt(Instant.now());
            stored.setLastTestSuccessful(success);
            stored.setLastTestError(success ? null : detail);
            repository.save(stored);
        }

        // Recorded whether or not it succeeded: a failed test is how an operator discovers a relay
        // has stopped accepting mail, and a succeeded one is a note that this server can now email.
        auditService.record(
                AuditAction.SMTP_TEST_EMAIL_SENT,
                stored == null ? null : stored.getId(),
                connection.host(),
                success
                        ? "Test email delivered to " + recipient
                        : "Test email to " + recipient + " failed: " + detail);

        return success
                ? new SendTestEmailResponse(true, "Test email sent to " + recipient + ".", null)
                : new SendTestEmailResponse(false, "Could not send the test email.", detail);
    }

    /**
     * Decrypts the stored password into a ready-to-use connection. Package-private: the plaintext
     * should travel no further than {@link SmtpMailSender}.
     */
    SmtpConnection toConnection(SmtpSettings settings) {
        return new SmtpConnection(
                settings.getHost(),
                settings.getPort() == null ? 0 : settings.getPort(),
                settings.getTransportSecurity(),
                settings.getUsername(),
                decryptPassword(settings),
                settings.getFromAddress(),
                settings.getFromName(),
                settings.getReplyToAddress());
    }

    /**
     * Builds the connection a test should use: the submitted values when present, otherwise the
     * stored ones.
     *
     * <p>The password follows the same "null means unchanged" rule as {@link #save}, so testing an
     * edited host without retyping the secret works. That is the common case — an administrator
     * who has saved a working relay and is changing one field.
     */
    private SmtpConnection resolveForTest(SendTestEmailRequest request, SmtpSettings stored) {
        UpdateSmtpSettingsRequest submitted = request.settings();
        if (submitted == null) {
            if (stored == null || !stored.isUsable()) {
                throw new BusinessRuleViolationException(
                        ErrorCode.MAIL_NOT_CONFIGURED,
                        "Configure and enable an SMTP relay, or include settings with the test request.");
            }
            return toConnection(stored);
        }

        requireCompleteForSending(submitted);
        String password = submitted.password() == null
                ? (stored == null ? null : decryptPassword(stored))
                : emptyToNull(submitted.password());

        return new SmtpConnection(
                submitted.host().strip(),
                submitted.port(),
                submitted.transportSecurity(),
                trimToNull(submitted.username()),
                password,
                submitted.fromAddress().strip(),
                trimToNull(submitted.fromName()),
                trimToNull(submitted.replyToAddress()));
    }

    /**
     * The fields a relay cannot do without. Bean validation cannot express this: the same fields
     * are optional while mail is off and required the moment it is switched on or tested.
     */
    private static void requireCompleteForSending(UpdateSmtpSettingsRequest request) {
        List<String> missing = new ArrayList<>();
        if (isBlank(request.host())) {
            missing.add("host");
        }
        if (request.port() == null) {
            missing.add("port");
        }
        if (isBlank(request.fromAddress())) {
            missing.add("fromAddress");
        }
        if (!missing.isEmpty()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.INVALID_MAIL_SETTINGS,
                    "Sending email requires " + String.join(", ", missing) + ".");
        }
    }

    private void applyPassword(SmtpSettings settings, String submitted) {
        if (submitted == null) {
            return;
        }
        if (submitted.isEmpty()) {
            settings.setPasswordEncrypted(null);
            return;
        }
        if (!encryptionService.isConfigured()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.INVALID_MAIL_SETTINGS,
                    "An SMTP password cannot be stored because no at-rest encryption key is "
                            + "available. Set app.security.mfa.encryption-key "
                            + "(APP_SECURITY_MFA_ENCRYPTION_KEY) and try again.");
        }
        settings.setPasswordEncrypted(encryptionService.encrypt(submitted));
    }

    /**
     * @return null when there is no stored password, or when the stored ciphertext cannot be opened
     *     with the current key. The second case is a rotated-away key: treating it as "no password"
     *     lets the send fail at the relay with a clear authentication error, which points at the
     *     right fix far better than an exception from the cipher would.
     */
    private String decryptPassword(SmtpSettings settings) {
        if (!settings.hasPassword()) {
            return null;
        }
        if (!encryptionService.isConfigured()) {
            log.warn("Stored SMTP password cannot be decrypted: no at-rest encryption key is configured.");
            return null;
        }
        try {
            return encryptionService.decrypt(settings.getPasswordEncrypted());
        } catch (RuntimeException e) {
            log.warn("Stored SMTP password could not be decrypted with the active encryption key; "
                    + "re-enter it in Settings -> Email (SMTP).");
            return null;
        }
    }

    /** Field names only — never a value, and never the password. */
    private static List<String> describeChanges(SmtpSettings before, UpdateSmtpSettingsRequest after) {
        List<String> changes = new ArrayList<>();
        if (before.isEnabled() != Boolean.TRUE.equals(after.enabled())) {
            changes.add(Boolean.TRUE.equals(after.enabled()) ? "email enabled" : "email disabled");
        }
        addIfChanged(changes, "host", before.getHost(), trimToNull(after.host()));
        addIfChanged(changes, "port",
                before.getPort() == null ? null : String.valueOf(before.getPort()),
                after.port() == null ? null : String.valueOf(after.port()));
        addIfChanged(changes, "transport security",
                nameOf(before.getTransportSecurity()), nameOf(after.transportSecurity()));
        addIfChanged(changes, "username", before.getUsername(), trimToNull(after.username()));
        addIfChanged(changes, "from address", before.getFromAddress(), trimToNull(after.fromAddress()));
        addIfChanged(changes, "from name", before.getFromName(), trimToNull(after.fromName()));
        addIfChanged(changes, "reply-to address",
                before.getReplyToAddress(), trimToNull(after.replyToAddress()));
        if (after.password() != null) {
            changes.add(after.password().isEmpty() ? "password cleared" : "password changed");
        }
        return changes;
    }

    private static void addIfChanged(List<String> changes, String field, String before, String after) {
        if (!java.util.Objects.equals(before, after)) {
            changes.add(field + " changed");
        }
    }

    private static String nameOf(SmtpTransportSecurity value) {
        return value == null ? null : value.name();
    }

    private static SmtpSettingsResponse toResponse(SmtpSettings settings) {
        return new SmtpSettingsResponse(
                settings.isEnabled(),
                settings.isUsable(),
                settings.getHost(),
                settings.getPort(),
                settings.getTransportSecurity(),
                settings.getUsername(),
                settings.hasPassword(),
                settings.getFromAddress(),
                settings.getFromName(),
                settings.getReplyToAddress(),
                settings.getUpdatedBy(),
                settings.getUpdatedAt(),
                settings.getLastTestAt(),
                settings.getLastTestSuccessful(),
                settings.getLastTestError());
    }

    private static String currentActor() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
