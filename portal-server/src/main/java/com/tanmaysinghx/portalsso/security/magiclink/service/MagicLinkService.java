package com.tanmaysinghx.portalsso.security.magiclink.service;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.mail.entity.EmailSettings;
import com.tanmaysinghx.portalsso.mail.entity.EmailTemplateType;
import com.tanmaysinghx.portalsso.mail.service.EmailSettingsService;
import com.tanmaysinghx.portalsso.mail.service.MailService;
import com.tanmaysinghx.portalsso.mail.service.NotificationService;
import com.tanmaysinghx.portalsso.mail.service.SignInDetails;
import com.tanmaysinghx.portalsso.registration.config.RegistrationProperties;
import com.tanmaysinghx.portalsso.security.device.DeviceSignature;
import com.tanmaysinghx.portalsso.security.magiclink.entity.MagicLinkPurpose;
import com.tanmaysinghx.portalsso.security.magiclink.entity.MagicLinkToken;
import com.tanmaysinghx.portalsso.security.magiclink.repository.MagicLinkTokenRepository;
import com.tanmaysinghx.portalsso.security.token.OpaqueTokens;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and redeems passwordless sign-in links.
 *
 * <h2>What makes this safe enough to sign someone in</h2>
 *
 * <ul>
 *   <li><strong>256 bits of {@code SecureRandom}</strong>, URL-safe Base64. Not guessable, so there
 *       is nothing for a rate limiter to have to protect.
 *   <li><strong>Only a SHA-256 is stored.</strong> A database dump is not a stack of working
 *       sign-in links. No slow KDF: there is no low-entropy input to brute-force the way there is
 *       with a password.
 *   <li><strong>Single use, short lived</strong>, and issuing a new link cancels the outstanding
 *       ones — a mailbox should never hold three working credentials.
 *   <li><strong>Constant-time comparison</strong> on redeem, so response timing cannot be used to
 *       confirm a prefix.
 *   <li><strong>Purpose-bound</strong>: a link minted to create an account cannot sign in to an
 *       existing one, or the reverse.
 *   <li><strong>MFA is not bypassed.</strong> A second factor exists precisely so that one
 *       compromised channel is not enough, and email is a channel. An account with MFA enabled
 *       still gets the challenge.
 * </ul>
 *
 * <h2>Why requesting a link never reveals whether an account exists</h2>
 *
 * <p>{@link #requestLink} returns the same answer whatever happens. This is a departure from
 * {@code PublicRegistrationController}, which deliberately accepts enumeration because telling a
 * user "that address is taken" is the only way they can act on it. Here there is no such cost: the
 * page says "check your email" either way, and a real user does get mail. The one dead end —
 * someone with no account on a server where magic-link registration is off — is unavoidable
 * without leaking, and that is the right side to err on for an unauthenticated endpoint that sends
 * mail to an address the caller chose.
 */
@Service
public class MagicLinkService {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);

    private final MagicLinkTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailSettingsService emailSettingsService;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final RegistrationProperties registrationProperties;
    private final MagicLinkProperties magicLinkProperties;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public MagicLinkService(
            MagicLinkTokenRepository tokenRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            EmailSettingsService emailSettingsService,
            NotificationService notificationService,
            MailService mailService,
            RegistrationProperties registrationProperties,
            MagicLinkProperties magicLinkProperties,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.emailSettingsService = emailSettingsService;
        this.notificationService = notificationService;
        this.mailService = mailService;
        this.registrationProperties = registrationProperties;
        this.magicLinkProperties = magicLinkProperties;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /** Whether the feature is on, for the sign-in page to decide whether to offer it. */
    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return emailSettingsService.currentSettings().isMagicLinkEnabled();
    }

    @Transactional(readOnly = true)
    public boolean isRegistrationEnabled() {
        EmailSettings settings = emailSettingsService.currentSettings();
        return settings.isMagicLinkEnabled() && settings.isMagicLinkRegistrationEnabled();
    }

    /**
     * Sends a link, if there is anything to send. The caller gets no signal either way.
     *
     * @throws BusinessRuleViolationException when the feature is switched off — that is a property
     *     of the server, not of the address, so saying so leaks nothing.
     */
    @Transactional
    public void requestLink(String rawEmail, String requesterIp, String userAgent) {
        EmailSettings settings = emailSettingsService.currentSettings();
        if (!settings.isMagicLinkEnabled()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.MAGIC_LINK_DISABLED, "Passwordless sign-in is not enabled on this server.");
        }

        // Checked BEFORE the address is looked at, and that ordering is the whole point. Deferring
        // it until the send meant a known address got a 503 from the failed send while an unknown
        // one got a cheerful 200 — a perfect account-enumeration oracle on any deployment whose
        // relay was misconfigured. Both now fail identically, and the failure describes the server
        // rather than the address.
        if (!mailService.isEnabled()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.MAIL_NOT_CONFIGURED,
                    "This server cannot send email yet, so a sign-in link cannot be delivered.");
        }

        String email = normalize(rawEmail);
        Instant now = Instant.now();

        if (isOverIssueLimit(email, now)) {
            // Silent, like every other branch here: telling the caller they are rate limited would
            // confirm the address is one worth limiting.
            log.info("Magic link issue limit reached for an address; request ignored");
            return;
        }

        Optional<User> existing = userRepository.findByEmail(email);
        MagicLinkPurpose purpose;
        if (existing.isPresent()) {
            User user = existing.get();
            if (!user.isEnabled() || user.isAccountLocked()) {
                // A lockout exists to stop sign-ins. A channel that bypasses it is not a feature.
                log.info("Magic link requested for a disabled or locked account; request ignored");
                return;
            }
            purpose = MagicLinkPurpose.SIGN_IN;
        } else if (settings.isMagicLinkRegistrationEnabled()) {
            purpose = MagicLinkPurpose.SIGN_UP;
        } else {
            log.info("Magic link requested for an unknown address and registration is off; request ignored");
            return;
        }

        // Supersede anything outstanding before minting a replacement.
        tokenRepository.consumeAllOutstandingFor(email, now);

        String token = OpaqueTokens.generate();
        int ttlMinutes = settings.getMagicLinkTtlMinutes();
        tokenRepository.save(new MagicLinkToken(
                OpaqueTokens.hash(token), email, purpose, now.plus(Duration.ofMinutes(ttlMinutes)), requesterIp));

        String linkUrl = emailSettingsService.currentBranding().portalUrl()
                + "/magic-link?token=" + token;

        EmailTemplateType type = purpose == MagicLinkPurpose.SIGN_UP
                ? EmailTemplateType.MAGIC_LINK_SIGN_UP
                : EmailTemplateType.MAGIC_LINK_SIGN_IN;

        // Synchronous, so a relay that is configured but failing is discovered now rather than
        // after the user has been told to go and check their mail.
        //
        // But the failure is swallowed, which is a deliberate and uncomfortable trade. Letting it
        // propagate would make a send failure visible only for addresses that have an account —
        // the same enumeration oracle the pre-flight check above closes, reopened one layer down.
        // Since the whole value of this endpoint's uniform response is that it is uniform, the
        // security property wins and the diagnostics go where operators actually look: the server
        // log, and the relay's last-test state on the SMTP settings tab.
        try {
            notificationService.sendMagicLink(
                    type,
                    email,
                    linkUrl,
                    ttlMinutes,
                    SignInDetails.of(DeviceSignature.parse(userAgent).label(), requesterIp, null));
        } catch (RuntimeException e) {
            log.error("Magic link could not be delivered; the caller was told it was sent. "
                    + "Check the relay on Settings -> Email (SMTP). Cause: {}", e.getMessage());
        }

        auditService.record(
                AuditAction.MAGIC_LINK_REQUESTED,
                existing.map(User::getId).orElse(null),
                email,
                "purpose=" + purpose + ", ttlMinutes=" + ttlMinutes);
    }

    /**
     * Validates and consumes a token, creating the account when the link was issued for sign-up.
     *
     * @throws BusinessRuleViolationException with a distinct error code for each reason, so the
     *     landing page can explain what to do rather than saying "invalid link". These answers are
     *     safe to differentiate: the caller already holds an unguessable token, so there is nothing
     *     left to enumerate.
     */
    @Transactional
    public Redemption redeem(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.MAGIC_LINK_INVALID, "This sign-in link is not valid.");
        }
        if (!emailSettingsService.currentSettings().isMagicLinkEnabled()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.MAGIC_LINK_DISABLED, "Passwordless sign-in is not enabled on this server.");
        }

        Instant now = Instant.now();
        MagicLinkToken token = tokenRepository.findByTokenHash(OpaqueTokens.hash(rawToken))
                .filter(candidate -> OpaqueTokens.constantTimeEquals(candidate.getTokenHash(), OpaqueTokens.hash(rawToken)))
                .orElseThrow(() -> new BusinessRuleViolationException(
                        ErrorCode.MAGIC_LINK_INVALID, "This sign-in link is not valid."));

        if (token.isConsumed()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.MAGIC_LINK_ALREADY_USED,
                    "This link has already been used. Request a new one to sign in.");
        }
        if (token.isExpired(now)) {
            throw new BusinessRuleViolationException(
                    ErrorCode.MAGIC_LINK_EXPIRED,
                    "This link has expired. Request a new one to sign in.");
        }

        // Consumed before anything else happens, so a failure further down cannot leave a link
        // that is still redeemable after it has had an effect.
        token.setConsumedAt(now);
        tokenRepository.save(token);

        String email = token.getEmail();
        return switch (token.getPurpose()) {
            case SIGN_IN -> redeemSignIn(email);
            case SIGN_UP -> redeemSignUp(email);
        };
    }

    private Redemption redeemSignIn(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        ErrorCode.MAGIC_LINK_INVALID, "This sign-in link is not valid."));

        // Re-checked at redeem time, not just at issue time: an administrator may have disabled the
        // account in the minutes between, and that decision has to win.
        if (!user.isEnabled()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.ACCOUNT_DISABLED, "This account is disabled. Contact an administrator.");
        }
        if (user.isAccountLocked()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.ACCOUNT_LOCKED, "This account is locked. Contact an administrator.");
        }

        auditService.record(AuditAction.MAGIC_LINK_SIGN_IN, user.getId(), email, "Signed in with a magic link");
        return new Redemption(email, user.isMfaEnabled(), false);
    }

    private Redemption redeemSignUp(String email) {
        // Won the race with an ordinary registration. Signing them in is the friendlier outcome and
        // is safe: the link proves the address, which is the only thing sign-up was establishing.
        Optional<User> alreadyExists = userRepository.findByEmail(email);
        if (alreadyExists.isPresent()) {
            return redeemSignIn(email);
        }

        if (!emailSettingsService.currentSettings().isMagicLinkRegistrationEnabled()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.MAGIC_LINK_DISABLED,
                    "Creating accounts with a link is not enabled on this server.");
        }

        String roleName = registrationProperties.defaultRole();
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName, "Magic-link registered user")));

        // No password is ever set, so the hash is of a value nobody holds — not a sentinel, which
        // would be a shared known plaintext across every such account. Password sign-in is
        // therefore impossible for this account until an administrator sets one, which is the
        // intended behaviour rather than a gap.
        User user = new User(email, passwordEncoder.encode(OpaqueTokens.generate()));
        user.setEnabled(true);
        user.addRole(role);
        User saved = userRepository.save(user);

        auditService.record(
                AuditAction.MAGIC_LINK_SIGN_UP,
                saved.getId(),
                email,
                "role=" + roleName + ", passwordless");

        notificationService.sendWelcome(email, null);
        return new Redemption(email, false, true);
    }

    /** Deletes tokens whose usefulness and retention tail have both elapsed. */
    @Transactional
    public int purgeExpired(Instant now) {
        return tokenRepository.deleteExpiredBefore(now.minus(magicLinkProperties.retention()));
    }

    private boolean isOverIssueLimit(String email, Instant now) {
        Instant since = now.minus(magicLinkProperties.window());
        return tokenRepository.countByEmailAndCreatedAtAfter(email, since)
                >= magicLinkProperties.maxPerAddressPerWindow();
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * @param mfaRequired true when the account has a second factor, so the caller must route to the
     *     MFA challenge rather than completing the sign-in.
     * @param accountCreated true when this redemption created the account, so the caller can land
     *     the user somewhere that acknowledges it.
     */
    public record Redemption(String email, boolean mfaRequired, boolean accountCreated) {}
}
