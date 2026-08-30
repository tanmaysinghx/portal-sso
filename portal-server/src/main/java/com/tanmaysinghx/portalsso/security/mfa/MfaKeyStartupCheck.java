package com.tanmaysinghx.portalsso.security.mfa;

import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides, at startup, whether the MFA encryption key situation is survivable — and repairs it
 * where it can.
 *
 * <h2>Why this is not simply "warn" or simply "refuse"</h2>
 *
 * <p>Those two looked like the only options, and each was unattractive: a warning is ignored, and
 * refusing outright strands a deployment that already has enrolled users. But that trade only
 * exists without a way to re-key existing secrets. With one, the answer differs by state, and each
 * state has an obviously correct response:
 *
 * <ul>
 *   <li><strong>Key set, everything decrypts</strong> — nothing to do.
 *   <li><strong>Key set, some secrets written under an older key</strong> — re-encrypt them here.
 *       This is the upgrade path off the retired default, and it needs no operator action beyond
 *       setting a key.
 *   <li><strong>Key set, some secrets open under no known key</strong> — refuse. Those users cannot
 *       complete a challenge, and starting anyway would present that as working.
 *   <li><strong>No key, no secrets stored</strong> — warn and carry on. Nothing is at risk yet, and
 *       enrolment is refused anyway, so the unsafe state cannot be entered. Refusing to boot here
 *       would stop a fresh deployment for a feature nobody is using.
 *   <li><strong>No key, secrets stored</strong> — refuse. This is the state the retired default
 *       created, and it is the only one where real secrets are sitting under a published key.
 * </ul>
 *
 * <p>Refusing is safe precisely because the message says how to fix it, and the fix is one restart.
 * The alternative that <em>looks</em> friendlier — start up and quietly skip the MFA challenge —
 * would silently strip the second factor from every enrolled user, which is far worse than an
 * outage an operator can see.
 */
@Component
@Order(50)
public class MfaKeyStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MfaKeyStartupCheck.class);

    private final UserRepository userRepository;
    private final MfaEncryptionService encryptionService;

    public MfaKeyStartupCheck(UserRepository userRepository, MfaEncryptionService encryptionService) {
        this.userRepository = userRepository;
        this.encryptionService = encryptionService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> withSecrets = userRepository.findAllWithMfaSecret();

        if (!encryptionService.isConfigured()) {
            if (withSecrets.isEmpty()) {
                log.warn("""
                        app.security.mfa.encryption-key is not set, so multi-factor authentication is \
                        unavailable and enrolment will be refused. Set it \
                        (APP_SECURITY_MFA_ENCRYPTION_KEY) to enable MFA.""");
                return;
            }
            throw new IllegalStateException("""
                    %d user(s) have a stored MFA secret but app.security.mfa.encryption-key is not set. \
                    Earlier versions encrypted these under a default key that was published in the \
                    application source, so they must be re-keyed. Set \
                    app.security.mfa.encryption-key to a new secret value and restart: the existing \
                    secrets are re-encrypted under it automatically. Refusing to start rather than \
                    continue with second factors protected by a public key."""
                    .formatted(withSecrets.size()));
        }

        int reEncrypted = 0;
        int unreadable = 0;

        for (User user : withSecrets) {
            if (encryptionService.decryptsWithActiveKey(user.getMfaSecret())) {
                continue;
            }
            Optional<String> plaintext = encryptionService.decryptForMigration(user.getMfaSecret());
            if (plaintext.isEmpty()) {
                unreadable++;
                log.error(
                        "MFA secret for '{}' cannot be decrypted with the configured key, the previous key, "
                                + "or the retired default.",
                        user.getEmail());
                continue;
            }
            user.setMfaSecret(encryptionService.encrypt(plaintext.get()));
            userRepository.save(user);
            reEncrypted++;
        }

        if (unreadable > 0) {
            throw new IllegalStateException("""
                    %d MFA secret(s) could not be decrypted with any known key. If you rotated \
                    app.security.mfa.encryption-key, set app.security.mfa.previous-encryption-key to \
                    the old value and restart — they will be re-encrypted. If the old key is genuinely \
                    lost, an administrator must reset MFA for those users \
                    (POST /api/admin/users/{id}/mfa/reset) before this server will start."""
                    .formatted(unreadable));
        }

        if (reEncrypted > 0) {
            log.warn("""
                    Re-encrypted {} MFA secret(s) under the configured app.security.mfa.encryption-key. \
                    If this was a rotation you can now remove app.security.mfa.previous-encryption-key.""",
                    reEncrypted);
        }
    }
}
