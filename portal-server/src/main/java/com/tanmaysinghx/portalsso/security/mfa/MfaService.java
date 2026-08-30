package com.tanmaysinghx.portalsso.security.mfa;

import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.user.entity.RecoveryCode;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RecoveryCodeRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaService {

    private static final String ISSUER = "Portal SSO";
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final String RECOVERY_CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // Base32 unambiguous
    private static final int RECOVERY_CODE_LENGTH = 10;

    private final TotpService totpService;
    private final MfaEncryptionService mfaEncryptionService;
    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public MfaService(
            TotpService totpService,
            MfaEncryptionService mfaEncryptionService,
            UserRepository userRepository,
            RecoveryCodeRepository recoveryCodeRepository,
            PasswordEncoder passwordEncoder) {
        this.totpService = totpService;
        this.mfaEncryptionService = mfaEncryptionService;
        this.userRepository = userRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Generates a new unconfirmed secret and provisioning URI. Note: mfa_enabled remains false
     * until confirmed by {@link #confirmSetup(User, String)}.
     */
    @Transactional
    public MfaSetupResponse initiateSetup(User user) {
        String secret = totpService.generateSecret();
        String encryptedSecret = mfaEncryptionService.encrypt(secret);

        // Store encrypted secret temporarily on the user row, but do NOT enable MFA yet
        user.setMfaSecret(encryptedSecret);
        user.setLastMfaTimeStep(null);
        userRepository.save(user);

        String provisioningUri = totpService.generateProvisioningUri(user.getEmail(), secret, ISSUER);
        return new MfaSetupResponse(secret, provisioningUri);
    }

    /**
     * Confirms the setup with a 6-digit TOTP code from the user's authenticator app, flips
     * mfa_enabled to true, and generates 8 single-use recovery codes.
     */
    @Transactional
    public List<String> confirmSetup(User user, String confirmationCode) {
        if (user.getMfaSecret() == null) {
            throw new BusinessRuleViolationException(
                    ErrorCode.INVALID_MFA_CODE, "No MFA setup has been initiated for this account.");
        }

        String decryptedSecret = mfaEncryptionService.decrypt(user.getMfaSecret());
        long now = Instant.now().getEpochSecond();
        OptionalLong verifiedStep = totpService.verifyCode(decryptedSecret, confirmationCode, now, null);

        if (verifiedStep.isEmpty()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.INVALID_MFA_CODE, "Invalid verification code. Please scan the QR code and try again.");
        }

        user.setMfaEnabled(true);
        user.setLastMfaTimeStep(verifiedStep.getAsLong());
        userRepository.save(user);

        // Generate and persist single-use recovery codes
        return generateAndPersistRecoveryCodes(user);
    }

    /**
     * Validates a submitted code (either 6-digit TOTP or emergency recovery code) against the user.
     */
    @Transactional
    public boolean verifyMfaOrRecoveryCode(User user, String rawCode) {
        if (!user.isMfaEnabled() || user.getMfaSecret() == null || rawCode == null) {
            return false;
        }

        String cleaned = rawCode.trim().replaceAll("[\\s-]+", "").toUpperCase();
        long now = Instant.now().getEpochSecond();

        // 1. Check if it is a 6-digit numeric TOTP code
        if (cleaned.length() == 6 && cleaned.matches("\\d{6}")) {
            String decryptedSecret = mfaEncryptionService.decrypt(user.getMfaSecret());
            OptionalLong verifiedStep = totpService.verifyCode(
                    decryptedSecret, cleaned, now, user.getLastMfaTimeStep());

            if (verifiedStep.isPresent()) {
                user.setLastMfaTimeStep(verifiedStep.getAsLong());
                userRepository.save(user);
                return true;
            }
        }

        // 2. Check if it matches an unused recovery code
        List<RecoveryCode> unusedCodes = recoveryCodeRepository.findByUserAndUsedAtIsNull(user);
        for (RecoveryCode rc : unusedCodes) {
            if (passwordEncoder.matches(cleaned, rc.getCodeHash())) {
                rc.setUsedAt(Instant.now());
                recoveryCodeRepository.save(rc);
                return true;
            }
        }

        return false;
    }

    /**
     * Disables MFA for the user and deletes all stored recovery codes.
     */
    @Transactional
    public void disableMfa(User user) {
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setLastMfaTimeStep(null);
        userRepository.save(user);
        recoveryCodeRepository.deleteByUser(user);
    }

    /**
     * Administrative reset of user MFA when device is lost.
     */
    @Transactional
    public void adminResetMfa(User user) {
        disableMfa(user);
    }

    private List<String> generateAndPersistRecoveryCodes(User user) {
        recoveryCodeRepository.deleteByUser(user);

        List<String> plainCodes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String plainCode = generateSingleRecoveryCode();
            String normalized = plainCode.replaceAll("[\\s-]+", "").toUpperCase();
            String hash = passwordEncoder.encode(normalized);

            RecoveryCode rc = new RecoveryCode(user, hash);
            recoveryCodeRepository.save(rc);
            plainCodes.add(plainCode);
        }

        return plainCodes;
    }

    private String generateSingleRecoveryCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < RECOVERY_CODE_LENGTH; i++) {
            if (i > 0 && i % 5 == 0) {
                sb.append('-');
            }
            sb.append(RECOVERY_CODE_CHARS.charAt(secureRandom.nextInt(RECOVERY_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    public record MfaSetupResponse(String secret, String provisioningUri) {}
}
