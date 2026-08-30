package com.tanmaysinghx.portalsso.security.mfa;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Encrypts and decrypts TOTP secrets at rest using AES-256-GCM authenticated encryption, with a
 * random 12-byte IV per secret prepended to the ciphertext.
 *
 * <h2>Why there is no default key</h2>
 *
 * <p>This class used to fall back to a key hardcoded in its own source. The cryptography was fine;
 * the key management was not. For a self-hosted product a default key is a <em>published</em> key,
 * so every deployment that never set the property stored secrets that anyone with the source could
 * decrypt from a database dump — a full second-factor bypass, which is precisely what encrypting
 * them was supposed to prevent. Worse, it happened silently.
 *
 * <p>Now the key must be configured. When it is not, this service reports itself unconfigured,
 * enrolment is refused rather than performed unsafely, and {@link MfaKeyStartupCheck} decides at
 * startup whether that is merely a warning or a reason to stop.
 *
 * <h2>Rotation</h2>
 *
 * <p>{@link #decryptForMigration} tries the previous key and the retired default so existing
 * secrets can be re-encrypted under a real key on startup. Those keys are only ever used to
 * <em>read</em>; everything written uses the active key.
 */
@Service
public class MfaEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    /**
     * The key this class used to fall back to. Kept solely so an existing deployment's secrets can
     * be read once and re-encrypted under a real key — never used to encrypt anything. It is public
     * knowledge by definition: it was in a published source file.
     */
    static final String RETIRED_DEFAULT_KEY = "portal-sso-mfa-default-encryption-key-32b";

    private final SecretKey activeKey;
    private final List<SecretKey> migrationKeys;
    private final SecureRandom secureRandom = new SecureRandom();

    public MfaEncryptionService(MfaEncryptionProperties properties) {
        this.activeKey = properties.hasEncryptionKey() ? deriveKey(properties.encryptionKey()) : null;

        List<SecretKey> fallbacks = new ArrayList<>();
        if (properties.hasPreviousEncryptionKey()) {
            fallbacks.add(deriveKey(properties.previousEncryptionKey()));
        }
        fallbacks.add(deriveKey(RETIRED_DEFAULT_KEY));
        this.migrationKeys = List.copyOf(fallbacks);
    }

    public boolean isConfigured() {
        return activeKey != null;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        requireConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, activeKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt MFA secret", e);
        }
    }

    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isBlank()) {
            return null;
        }
        requireConfigured();
        return decryptWith(encryptedBase64, activeKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to decrypt MFA secret with the configured app.security.mfa.encryption-key"));
    }

    /**
     * Reads a secret written under an older key, for the startup re-encryption only.
     *
     * @return empty when no known key opens it — which means the secret is unrecoverable and the
     *     user must re-enrol, so the caller must surface that rather than swallow it.
     */
    Optional<String> decryptForMigration(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isBlank()) {
            return Optional.empty();
        }
        for (SecretKey key : migrationKeys) {
            Optional<String> plaintext = decryptWith(encryptedBase64, key);
            if (plaintext.isPresent()) {
                return plaintext;
            }
        }
        return Optional.empty();
    }

    /** True when the active key already opens this value, so it needs no migration. */
    boolean decryptsWithActiveKey(String encryptedBase64) {
        return activeKey != null
                && encryptedBase64 != null
                && !encryptedBase64.isBlank()
                && decryptWith(encryptedBase64, activeKey).isPresent();
    }

    /**
     * GCM authenticates as it decrypts, so a wrong key fails rather than returning plausible
     * garbage. That is what makes trying keys in turn a sound way to identify the right one.
     */
    private Optional<String> decryptWith(String encryptedBase64, SecretKey key) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            if (combined.length <= IV_LENGTH_BYTES) {
                return Optional.empty();
            }

            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return Optional.of(new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void requireConfigured() {
        if (activeKey == null) {
            throw new IllegalStateException(
                    "app.security.mfa.encryption-key is not configured; multi-factor authentication is unavailable.");
        }
    }

    private static SecretKey deriveKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize MFA encryption key", e);
        }
    }
}
