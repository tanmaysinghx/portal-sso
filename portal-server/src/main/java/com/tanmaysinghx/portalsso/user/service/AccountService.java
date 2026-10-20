package com.tanmaysinghx.portalsso.user.service;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.ResourceNotFoundException;
import com.tanmaysinghx.portalsso.security.device.DeviceSignature;
import com.tanmaysinghx.portalsso.security.device.entity.KnownDevice;
import com.tanmaysinghx.portalsso.security.device.repository.KnownDeviceRepository;
import com.tanmaysinghx.portalsso.security.mfa.MfaService;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.web.dto.AccountResponse;
import com.tanmaysinghx.portalsso.user.web.dto.KnownDeviceResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything the signed-in user can do to their own account: profile, password, recovery codes and
 * remembered devices. Each method acts on the caller's own row only — the email always comes from
 * the authenticated principal, never from the request body.
 */
@Service
public class AccountService {

    private static final int MAX_NAME_LENGTH = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final MfaService mfaService;
    private final KnownDeviceRepository knownDeviceRepository;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final AuditService auditService;

    public AccountService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            MfaService mfaService,
            KnownDeviceRepository knownDeviceRepository,
            FindByIndexNameSessionRepository<? extends Session> sessionRepository,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.mfaService = mfaService;
        this.knownDeviceRepository = knownDeviceRepository;
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String email) {
        return toResponse(load(email));
    }

    /**
     * Updates the display name. Blank clears a field. Audited with the names of the fields that
     * changed, never the values — a name is personal data the audit log has no reason to hold.
     */
    @Transactional
    public AccountResponse updateProfile(String email, String firstName, String lastName) {
        User user = load(email);
        String newFirst = normaliseName(firstName);
        String newLast = normaliseName(lastName);

        List<String> changed = new ArrayList<>();
        if (!Objects.equals(user.getFirstName(), newFirst)) {
            changed.add("firstName");
            user.setFirstName(newFirst);
        }
        if (!Objects.equals(user.getLastName(), newLast)) {
            changed.add("lastName");
            user.setLastName(newLast);
        }

        if (!changed.isEmpty()) {
            userRepository.save(user);
            auditService.record(
                    AuditAction.USER_PROFILE_UPDATED, user.getId(), user.getEmail(), "changed=" + String.join(",", changed));
        }
        return toResponse(user);
    }

    /**
     * Changes the caller's password and signs out every <em>other</em> session they hold.
     *
     * <p>The current password is required even though the caller is signed in: a session is a
     * weaker proof than the password, and without this check anyone who briefly holds an unlocked
     * browser could turn that into permanent access. Other sessions go because "change my password"
     * is what people do when they suspect someone else is in.
     *
     * @return how many other sessions were signed out.
     */
    @Transactional
    public int changePassword(String email, String currentPassword, String newPassword, String currentSessionId) {
        User user = load(email);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleViolationException(
                    ErrorCode.CURRENT_PASSWORD_INCORRECT, "The current password is incorrect.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessRuleViolationException(
                    ErrorCode.PASSWORD_UNCHANGED, "The new password must be different from the current one.");
        }
        passwordPolicy.validate(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        int signedOut = signOutOtherSessions(email, currentSessionId);
        auditService.record(
                AuditAction.USER_PASSWORD_CHANGED, user.getId(), user.getEmail(), "otherSessionsSignedOut=" + signedOut);
        return signedOut;
    }

    /**
     * Issues a fresh set of recovery codes. Proof is a current authenticator code (or an unused
     * recovery code) rather than the password, because accounts created by magic link have no
     * password the user knows — and the second factor is the thing these codes stand in for.
     */
    @Transactional
    public List<String> regenerateRecoveryCodes(String email, String code) {
        User user = load(email);
        if (!user.isMfaEnabled()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.INVALID_MFA_CODE, "Two-factor authentication is not enabled for this account.");
        }
        if (!mfaService.verifyMfaOrRecoveryCode(user, code)) {
            throw new BusinessRuleViolationException(
                    ErrorCode.INVALID_MFA_CODE, "That code is not valid. Enter the current code from your authenticator app.");
        }
        List<String> codes = mfaService.regenerateRecoveryCodes(user);
        auditService.record(AuditAction.USER_RECOVERY_CODES_REGENERATED, user.getId(), user.getEmail(), null);
        return codes;
    }

    @Transactional(readOnly = true)
    public List<KnownDeviceResponse> listDevices(String email, String currentUserAgent) {
        User user = load(email);
        String currentHash = DeviceSignature.parse(currentUserAgent).hash();
        return knownDeviceRepository.findByUserIdOrderByLastSeenAtDesc(user.getId()).stream()
                .map(device -> KnownDeviceResponse.from(device, device.getDeviceHash().equals(currentHash)))
                .toList();
    }

    /**
     * Forgets one remembered device. Safe in the direction that matters: the device is not blocked,
     * it simply counts as new again, so its next sign-in raises an alert.
     */
    @Transactional
    public void forgetDevice(String email, UUID deviceId) {
        User user = load(email);
        KnownDevice device = knownDeviceRepository.findById(deviceId)
                // Someone else's device answers exactly like a missing one, so ids cannot be probed.
                .filter(d -> d.getUserId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DEVICE_NOT_FOUND, "No such device."));
        knownDeviceRepository.delete(device);
        auditService.record(AuditAction.USER_DEVICE_FORGOTTEN, user.getId(), user.getEmail(), device.getDeviceLabel());
    }

    private int signOutOtherSessions(String email, String currentSessionId) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(email);
        int count = 0;
        for (String sessionId : sessions.keySet()) {
            if (!sessionId.equals(currentSessionId)) {
                sessionRepository.deleteById(sessionId);
                count++;
            }
        }
        return count;
    }

    private AccountResponse toResponse(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getName).sorted().toList();
        return new AccountResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                roles,
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.isMfaEnabled(),
                mfaService.isAvailable(),
                user.isMfaEnabled() ? mfaService.countUnusedRecoveryCodes(user) : 0,
                passwordPolicy.describe(),
                user.getPasswordChangedAt(),
                passwordPolicy.expiresAt(user.getPasswordChangedAt()).orElse(null));
    }

    private User load(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: " + email));
    }

    private static String normaliseName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessRuleViolationException(
                    ErrorCode.INVALID_USER_DATA, "Names must be at most " + MAX_NAME_LENGTH + " characters.");
        }
        return trimmed;
    }
}
