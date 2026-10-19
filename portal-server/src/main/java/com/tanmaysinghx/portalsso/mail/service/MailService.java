package com.tanmaysinghx.portalsso.mail.service;

import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.PortalException;
import com.tanmaysinghx.portalsso.mail.entity.SmtpSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

/**
 * The application's entry point for sending mail. Features that need to email a user call this and
 * nothing lower.
 *
 * <h2>Failure is loud</h2>
 *
 * <p>{@link #send} throws rather than returning a boolean. Its callers are things like a
 * password-reset request, where silently not sending leaves someone waiting for a message that will
 * never arrive — worse than an error they can act on. A caller that genuinely wants mail to be
 * best-effort (a notification nobody is blocked on) should check {@link #isEnabled()} first, or
 * catch {@link PortalException} and say at the call site why it is being swallowed.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final SmtpSettingsService settingsService;
    private final SmtpMailSender mailSender;

    public MailService(SmtpSettingsService settingsService, SmtpMailSender mailSender) {
        this.settingsService = settingsService;
        this.mailSender = mailSender;
    }

    /**
     * Whether a send would actually be attempted: mail is switched on and the configuration is
     * complete. Optional mail should be guarded with this, so a deployment that never configured a
     * relay behaves as though the feature is simply off rather than erroring on every attempt.
     */
    public boolean isEnabled() {
        return settingsService.currentSettings().isUsable();
    }

    /**
     * Sends a plain-text message through the stored configuration.
     *
     * @throws PortalException {@code MAIL_NOT_CONFIGURED} when no usable relay is configured, or
     *     {@code MAIL_SEND_FAILED} when the relay could not be reached or rejected the message.
     */
    public void send(String to, String subject, String body) {
        SmtpSettings settings = settingsService.currentSettings();
        if (!settings.isUsable()) {
            throw new PortalException(ErrorCode.MAIL_NOT_CONFIGURED);
        }
        SmtpConnection connection = settingsService.toConnection(settings);
        try {
            mailSender.send(connection, to, subject, body);
        } catch (MailException | SmtpMailSender.MessagingFailure e) {
            // The relay host and the envelope addresses belong in the server log; the password does
            // not, and is not interpolated into anything here.
            log.warn("Failed to send mail to {} via {}:{}", to, connection.host(), connection.port(), e);
            throw new PortalException(ErrorCode.MAIL_SEND_FAILED, SmtpFailures.describe(e));
        }
    }
}
