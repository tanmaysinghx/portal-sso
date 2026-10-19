package com.tanmaysinghx.portalsso.mail.service;

import com.tanmaysinghx.portalsso.mail.entity.EmailTemplateType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends the product's notification emails. The rest of the application calls this and never touches
 * templates or SMTP directly.
 *
 * <h2>Two delivery policies, on purpose</h2>
 *
 * <p><strong>Notifications are fire-and-forget</strong> ({@code @Async}, failures swallowed and
 * logged). Nobody is waiting on a welcome message, and a relay that has started refusing mail must
 * not be able to break sign-in or account creation. This is the same stance
 * {@code LoginEventRecorder} takes, and for the same reason.
 *
 * <p><strong>A magic link is sent synchronously and throws on failure.</strong> There the user is
 * staring at a page that is about to say "check your email", and saying that when the send failed
 * leaves them waiting for something that will never arrive — the one outcome worse than an error
 * they can retry.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** UTC and spelled out, because the recipient's timezone is not knowable from a mail send. */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final EmailSettingsService emailSettingsService;
    private final EmailTemplateRenderer renderer;
    private final MailService mailService;

    public NotificationService(
            EmailSettingsService emailSettingsService,
            EmailTemplateRenderer renderer,
            MailService mailService) {
        this.emailSettingsService = emailSettingsService;
        this.renderer = renderer;
        this.mailService = mailService;
    }

    /** Sent once, when an account is created — by an administrator, self-registration, or a magic link. */
    @Async(MailAsyncConfig.EXECUTOR)
    public void sendWelcome(String email, String firstName) {
        Map<String, Object> model = baseModel(firstName);
        model.put("signInUrl", emailSettingsService.currentBranding().portalUrl() + "/sign-in");
        sendQuietly(EmailTemplateType.WELCOME, email, model);
    }

    /** The security alert: this account just signed in from a device it has not used before. */
    @Async(MailAsyncConfig.EXECUTOR)
    public void sendNewDeviceAlert(String email, String firstName, SignInDetails details) {
        sendQuietly(EmailTemplateType.NEW_DEVICE_SIGN_IN, email, signInModel(firstName, details));
    }

    /** The every-sign-in notice. Off by default — see {@link EmailTemplateType#SIGN_IN_NOTICE}. */
    @Async(MailAsyncConfig.EXECUTOR)
    public void sendSignInNotice(String email, String firstName, SignInDetails details) {
        sendQuietly(EmailTemplateType.SIGN_IN_NOTICE, email, signInModel(firstName, details));
    }

    /**
     * Sends a magic link and lets failures propagate, so the caller can tell the user the truth.
     *
     * @param type {@code MAGIC_LINK_SIGN_IN} or {@code MAGIC_LINK_SIGN_UP}.
     * @param linkUrl the absolute, single-use URL.
     * @throws com.tanmaysinghx.portalsso.common.error.PortalException when no relay is configured
     *     or the send failed.
     */
    public void sendMagicLink(
            EmailTemplateType type,
            String email,
            String linkUrl,
            int ttlMinutes,
            SignInDetails details) {
        Map<String, Object> model = signInModel(null, details);
        model.put("linkUrl", linkUrl);
        model.put("expiresInMinutes", ttlMinutes);
        send(type, email, model);
    }

    /**
     * Sends one template filled with sample data, ignoring the notification switches.
     *
     * <p>Synchronous and throwing: an administrator clicking "preview" is waiting for the answer,
     * and the answer they need is whether it worked.
     */
    public void sendPreview(EmailTemplateType type, String recipient) {
        EmailBranding branding = emailSettingsService.currentBranding();
        SignInDetails sample = new SignInDetails(
                "Chrome on macOS", "203.0.113.42", "Bengaluru, India", Instant.now());

        Map<String, Object> model = signInModel("Alex", sample);
        model.put("signInUrl", branding.portalUrl() + "/sign-in");
        model.put("linkUrl", branding.portalUrl() + "/magic-link?token=sample-preview-token");
        model.put("expiresInMinutes", emailSettingsService.currentSettings().getMagicLinkTtlMinutes());
        // Marks the message in the body as well as the subject. Previews go to a real mailbox, and
        // a new-device alert that looks genuine is alarming to receive by surprise.
        model.put("preview", true);

        RenderedEmail rendered = renderer.render(type, branding, model);
        mailService.send(recipient, "[Preview] " + rendered.subject(), rendered.text(), rendered.html());
    }

    private Map<String, Object> signInModel(String firstName, SignInDetails details) {
        Map<String, Object> model = baseModel(firstName);
        if (details != null) {
            model.put("deviceLabel", details.deviceLabel());
            model.put("ipAddress", details.ipAddress());
            model.put("location", details.location());
            model.put("hasLocation", details.hasLocation());
            model.put("occurredAt", TIMESTAMP.format(details.occurredAt()));
        }
        return model;
    }

    private Map<String, Object> baseModel(String firstName) {
        Map<String, Object> model = new HashMap<>();
        // Falls back to a greeting with no name rather than "Hello null" or "Hello there" — first
        // name is optional on a User, and most self-registered accounts never set one.
        model.put("firstName", firstName == null || firstName.isBlank() ? null : firstName.strip());
        model.put("preview", false);
        return model;
    }

    /** Honours the switch, renders, sends, and never lets a failure escape. */
    private void sendQuietly(EmailTemplateType type, String email, Map<String, Object> model) {
        try {
            if (!emailSettingsService.isNotificationEnabled(type)) {
                return;
            }
            if (!mailService.isEnabled()) {
                // Not a warning: a deployment with no relay is a supported configuration, and
                // logging at warn on every sign-in would fill the log with a non-problem.
                log.debug("Skipping {} to {}: no SMTP relay is configured", type, email);
                return;
            }
            send(type, email, model);
        } catch (RejectedExecutionException e) {
            log.warn("Mail executor is saturated; dropped {} to {}", type, email);
        } catch (Exception e) {
            log.warn("Could not send {} to {}: {}", type, email, e.getMessage());
        }
    }

    private void send(EmailTemplateType type, String email, Map<String, Object> model) {
        RenderedEmail rendered = renderer.render(type, emailSettingsService.currentBranding(), model);
        mailService.send(email, rendered.subject(), rendered.text(), rendered.html());
    }
}
