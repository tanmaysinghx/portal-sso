package com.tanmaysinghx.portalsso.security.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MfaEncryptionServiceTest {

    private MfaEncryptionService encryptionService;

    private static MfaEncryptionService withKeys(String active, String previous) {
        return new MfaEncryptionService(new MfaEncryptionProperties(active, previous));
    }

    @BeforeEach
    void setUp() {
        encryptionService = withKeys("test-secret-key-for-mfa-testing-12345", null);
    }

    @Test
    void encryptsAndDecryptsSuccessfully() {
        String secret = "JBSWY3DPEHPK3PXP4567";
        String encrypted = encryptionService.encrypt(secret);

        assertThat(encrypted).isNotBlank();
        assertThat(encrypted).isNotEqualTo(secret);

        String decrypted = encryptionService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(secret);
    }

    @Test
    void generatesDifferentCiphertextsForSamePlaintextDueToRandomIV() {
        String secret = "JBSWY3DPEHPK3PXP";
        String encrypted1 = encryptionService.encrypt(secret);
        String encrypted2 = encryptionService.encrypt(secret);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(secret);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(secret);
    }

    @Test
    void handlesNullAndBlankInputs() {
        assertThat(encryptionService.encrypt(null)).isNull();
        assertThat(encryptionService.encrypt("")).isNull();
        assertThat(encryptionService.decrypt(null)).isNull();
        assertThat(encryptionService.decrypt("")).isNull();
    }

    @Test
    void failsOnCorruptedCiphertext() {
        assertThatThrownBy(() -> encryptionService.decrypt("not-a-valid-base64-ciphertext"))
                .isInstanceOf(Exception.class);
    }

    /**
     * The point of the change: with no key configured there is no fallback to encrypt under, so the
     * service reports itself unusable rather than quietly using a key published in the source.
     */
    @Test
    void withNoKeyConfiguredTheServiceIsUnusableRatherThanFallingBack() {
        MfaEncryptionService unconfigured = withKeys(null, null);

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.encrypt("JBSWY3DPEHPK3PXP"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption-key");
    }

    /** A secret written under the retired default must still be readable, or upgrades strand users. */
    @Test
    void aSecretWrittenUnderTheRetiredDefaultCanStillBeRead() {
        String secret = "JBSWY3DPEHPK3PXP";
        String legacyCiphertext = withKeys(MfaEncryptionService.RETIRED_DEFAULT_KEY, null).encrypt(secret);

        MfaEncryptionService current = withKeys("a-brand-new-operator-supplied-key", null);

        assertThat(current.decryptsWithActiveKey(legacyCiphertext))
                .as("it was not written under the new key")
                .isFalse();
        assertThat(current.decryptForMigration(legacyCiphertext)).contains(secret);
    }

    @Test
    void aSecretWrittenUnderAPreviousKeyCanStillBeRead() {
        String secret = "JBSWY3DPEHPK3PXP";
        String oldCiphertext = withKeys("the-previous-key", null).encrypt(secret);

        MfaEncryptionService rotating = withKeys("the-new-key", "the-previous-key");

        assertThat(rotating.decryptsWithActiveKey(oldCiphertext)).isFalse();
        assertThat(rotating.decryptForMigration(oldCiphertext)).contains(secret);
    }

    /**
     * An unknown key must yield empty, not garbage. GCM authenticates as it decrypts, which is what
     * makes trying candidate keys in turn a sound way to identify the right one.
     */
    @Test
    void aSecretUnderAnUnknownKeyIsReportedUnreadable() {
        String orphaned = withKeys("a-key-nobody-kept", null).encrypt("JBSWY3DPEHPK3PXP");

        assertThat(withKeys("the-configured-key", "some-other-previous-key").decryptForMigration(orphaned))
                .isEmpty();
    }

    @Test
    void rotationDoesNotChangeWhatEncryptionProduces() {
        MfaEncryptionService rotating = withKeys("the-new-key", "the-previous-key");
        String ciphertext = rotating.encrypt("JBSWY3DPEHPK3PXP");

        // Everything written uses the active key; the fallbacks are read-only.
        assertThat(rotating.decryptsWithActiveKey(ciphertext)).isTrue();
        assertThat(withKeys("the-previous-key", null).decryptForMigration(ciphertext)).isEmpty();
    }
}
