package com.tanmaysinghx.portalsso.security.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The upgrade path off the retired default key.
 *
 * <p>This is what makes refusing to start on a missing key defensible rather than a lockout: an
 * existing deployment sets a real key, restarts once, and its stored secrets are re-encrypted in
 * place. Without this, the only honest options were to warn and stay unsafe, or to refuse and
 * strand every enrolled user.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mfakeymigration;MODE=PostgreSQL",
        "app.seed.test-data=false",
        "app.security.mfa.encryption-key=the-operator-supplied-key"
})
class MfaKeyMigrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MfaEncryptionService encryptionService;

    @Autowired
    private MfaKeyStartupCheck startupCheck;

    @Test
    @Transactional
    void aSecretStoredUnderTheRetiredDefaultIsReEncryptedOnStartup() {
        String plaintextSecret = "JBSWY3DPEHPK3PXP";
        String legacy = new MfaEncryptionService(
                new MfaEncryptionProperties(MfaEncryptionService.RETIRED_DEFAULT_KEY, null))
                .encrypt(plaintextSecret);

        User user = new User("legacy-mfa@example.com", "irrelevant");
        user.setMfaEnabled(true);
        user.setMfaSecret(legacy);
        User saved = userRepository.save(user);

        assertThat(encryptionService.decryptsWithActiveKey(legacy))
                .as("precondition: not yet readable under the configured key")
                .isFalse();

        startupCheck.run(null);

        String migrated = userRepository.findById(saved.getId()).orElseThrow().getMfaSecret();
        assertThat(migrated).isNotEqualTo(legacy);
        assertThat(encryptionService.decryptsWithActiveKey(migrated)).isTrue();
        // The secret itself must survive intact, or every enrolled authenticator app stops working.
        assertThat(encryptionService.decrypt(migrated)).isEqualTo(plaintextSecret);
    }

    @Test
    @Transactional
    void aSecretAlreadyUnderTheActiveKeyIsLeftAlone() {
        String ciphertext = encryptionService.encrypt("JBSWY3DPEHPK3PXP");
        User user = new User("current-mfa@example.com", "irrelevant");
        user.setMfaEnabled(true);
        user.setMfaSecret(ciphertext);
        User saved = userRepository.save(user);

        startupCheck.run(null);

        // Re-encrypting needlessly would churn the row and, with a random IV, hide whether the
        // migration is actually selective.
        assertThat(userRepository.findById(saved.getId()).orElseThrow().getMfaSecret()).isEqualTo(ciphertext);
    }

    /**
     * A secret no known key opens means that user cannot complete a challenge. Starting anyway would
     * present a broken second factor as working, so the server stops and names the count.
     */
    @Test
    @Transactional
    void aSecretUnderAnUnknownKeyStopsStartup() {
        String orphaned = new MfaEncryptionService(new MfaEncryptionProperties("a-key-nobody-kept", null))
                .encrypt("JBSWY3DPEHPK3PXP");

        User user = new User("orphaned-mfa@example.com", "irrelevant");
        user.setMfaEnabled(true);
        user.setMfaSecret(orphaned);
        userRepository.save(user);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> startupCheck.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be decrypted")
                .hasMessageContaining("previous-encryption-key");
    }

    @Test
    void aServerWithNoLegacySecretsStartsCleanly() {
        // No exception: the common case must not be made noisy by the migration path existing.
        startupCheck.run(null);
    }
}
