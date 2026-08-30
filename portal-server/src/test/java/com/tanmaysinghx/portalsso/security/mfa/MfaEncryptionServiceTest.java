package com.tanmaysinghx.portalsso.security.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MfaEncryptionServiceTest {

    private MfaEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new MfaEncryptionService("test-secret-key-for-mfa-testing-12345");
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
}
