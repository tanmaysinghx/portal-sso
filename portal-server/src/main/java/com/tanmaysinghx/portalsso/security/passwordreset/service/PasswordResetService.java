package com.tanmaysinghx.portalsso.security.passwordreset.service;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.mail.service.EmailSettingsService;
import com.tanmaysinghx.portalsso.mail.service.MailService;
import com.tanmaysinghx.portalsso.mail.service.NotificationService;
import com.tanmaysinghx.portalsso.mail.service.SignInDetails;
import com.tanmaysinghx.portalsso.security.device.DeviceSignature;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.security.passwordreset.entity.PasswordResetToken;
import com.tanmaysinghx.portalsso.security.passwordreset.repository.PasswordResetTokenRepository;
import com.tanmaysinghx.portalsso.security.token.OpaqueTokens;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Forgot password": emails a single-use link that lets the account holder set a new password.
 *
 * <p>Built to the same rules as {@code MagicLinkService} — 256-bit token, only its SHA-256 stored,
 * single use, short TTL, issuing cancels outstanding links, constant-time compare — and with the
 * same stance on enumeration: {@link #requestReset} answers identically for every address, and the
 * relay check runs before the address is looked at.
 *
 * <p>A reset does <strong>not</strong> sign anyone in. It sets the password and stops; the user then
 * signs in normally, which is where the second factor is asked for. An emailed link proves control
 * of a mailbox, and a second factor exists precisely so that one compromised channel is not enough.
 *
 * <p>A successful reset also clears a failed-attempt lockout. The lockout exists to stop password
 * guessing; someone who has proved they control the mailbox is not guessing, and leaving them locked
 * out of the account they just recovered would send every forgotten password to an administrator.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final MailService mailService;
    private final NotificationService notificationService;
    private final EmailSettingsService emailSettingsService;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final PasswordResetProperties properties;
    private final AuditService auditService;

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            MailService mailService,
            NotificationService notificationService,
            EmailSettingsService emailSettingsService,
            FindByIndexNameSessionRepository<? extends Session> sessionRepository,
            PasswordResetProperties properties,
            AuditService auditService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.mailService = mailService;
        this.notificationService = notificationService;
        this.emailSettingsService = emailSettingsService;
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.auditService = auditService;
    }

    /**
     * Whether the sign-in pages should offer "Forgot password?" at all. Describes the server, not
     * any address, so it leaks nothing.
     */
    public boolean isAvailable() {
        return mailService.isEnabled();
    }

    /**
     * Emails a reset link if the address belongs to an account that can use one. The caller gets no
     * signal either way.
     *
     * @throws BusinessRuleViolationException only when this server cannot send mail at all.
     */
    @Transactional
    public void requestReset(String rawEmail, String requesterIp, String userAgent) {
        // Before the address is looked at, for the reason spelled out in MagicLinkService: checked
        // later, a send failure would surface only for addresses that have an account.
        if (!mailService.isEnabled()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.MAIL_NOT_CONFIGURED,
                    "This server cannot send email, so a reset link cannot be delivered. Contact an administrator.");
        }

        String email = normalize(rawEmail);
        Instant now = Instant.now();

        if (tokenRepository.countByEmailAndCreatedAtAfter(email, now.minus(properties.window()))
                >= properties.maxPerAddressPerWindow()) {
            log.info("Password reset issue limit reached for an address; request ignored");
            return;
        }

        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            log.info("Password reset requested for an unknown address; request ignored");
            return;
        }
        User user = found.get();
        if (!user.isEnabled()) {
            // A disabled account is an administrator's decision, and a reset must not route round it.
            log.info("Password reset requested for a disabled account; request ignored");
            return;
        }

        tokenRepository.consumeAllOutstandingFor(user.getId(), now);

        String token = OpaqueTokens.generate();
        tokenRepository.save(new PasswordResetToken(
                OpaqueTokens.hash(token), user.getId(), email, now.plus(properties.ttl()), requesterIp));

        String linkUrl = emailSettingsService.currentBranding().portalUrl() + "/reset-password?token=" + token;
        try {
            notificationService.sendPasswordReset(
                    email,
                    user.getFirstName(),
                    linkUrl,
                    (int) properties.ttl().toMinutes(),
                    SignInDetails.of(DeviceSignature.parse(userAgent).label(), requesterIp, null));
        } catch (RuntimeException e) {
            // Swallowed so the response stays uniform; see MagicLinkService.requestLink.
            log.error("Password reset email could not be delivered; the caller was told it was sent. "
                    + "Check the relay on Settings -> Email delivery. Cause: {}", e.getMessage());
        }

        auditService.record(
                AuditAction.PASSWORD_RESET_REQUESTED, user.getId(), email, "ttlMinutes=" + properties.ttl().toMinutes());
    }

    /**
     * Checks a link without using it, so the reset page can say "expired" before the user has typed
     * a new password rather than after.
     *
     * @return the address the link belongs to — safe to show, the caller already holds the link.
     */
    @Transactional(readOnly = true)
    public String inspect(String rawToken) {
        return load(rawToken, Instant.now()).getEmail();
    }

    /**
     * Sets the new password, consumes the link and every other outstanding one, clears any lockout
     * and signs out every session the account holds.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken token = load(rawToken, now);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        ErrorCode.PASSWORD_RESET_INVALID, "This password reset link is not valid."));
        if (!user.isEnabled()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.ACCOUNT_DISABLED, "This account is disabled. Contact an administrator.");
        }

        // Judged before the link is spent, so a password the policy rejects leaves the link usable
        // for a second attempt instead of sending the user back to their mailbox.
        passwordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessRuleViolationException(
                    ErrorCode.PASSWORD_UNCHANGED, "Choose a password different from your current one.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        userRepository.save(user);

        tokenRepository.consumeAllOutstandingFor(user.getId(), now);

        int signedOut = 0;
        for (String sessionId : Map.copyOf(sessionRepository.findByPrincipalName(user.getEmail())).keySet()) {
            sessionRepository.deleteById(sessionId);
            signedOut++;
        }

        auditService.record(
                AuditAction.PASSWORD_RESET_COMPLETED, user.getId(), user.getEmail(), "sessionsSignedOut=" + signedOut);
    }

    @Transactional
    public int purgeExpired(Instant now) {
        return tokenRepository.deleteExpiredBefore(now.minus(properties.retention()));
    }

    private PasswordResetToken load(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.PASSWORD_RESET_INVALID, "This password reset link is not valid.");
        }
        String hash = OpaqueTokens.hash(rawToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(hash)
                .filter(candidate -> OpaqueTokens.constantTimeEquals(candidate.getTokenHash(), hash))
                .orElseThrow(() -> new BusinessRuleViolationException(
                        ErrorCode.PASSWORD_RESET_INVALID, "This password reset link is not valid."));
        if (token.isConsumed()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.PASSWORD_RESET_ALREADY_USED,
                    "This link has already been used. Request a new one if you still need to reset your password.");
        }
        if (token.isExpired(now)) {
            throw new BusinessRuleViolationException(
                    ErrorCode.PASSWORD_RESET_EXPIRED, "This link has expired. Request a new one.");
        }
        return token;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
