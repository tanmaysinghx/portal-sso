package com.tanmaysinghx.portalsso.security.mfa;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * RFC 6238 compliant Time-Based One-Time Password (TOTP) generator and verifier.
 *
 * <p>Standard parameters: HMAC-SHA1, 6 digits, 30-second time steps, Base32 encoding. Supports a
 * ±1 time-step window for clock drift tolerance, and returns the verified time-step to enable
 * replay prevention.
 */
@Service
public class TotpService {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int MODULO = 1_000_000;
    private static final int SECRET_BYTES_LENGTH = 20; // 160-bit secret

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically random 160-bit Base32 TOTP secret.
     */
    public String generateSecret() {
        byte[] buffer = new byte[SECRET_BYTES_LENGTH];
        secureRandom.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    /**
     * Formats an RFC-compliant {@code otpauth://} URI for authenticator app enrollment.
     */
    public String generateProvisioningUri(String email, String secret, String issuer) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedAccount = URLEncoder.encode(email, StandardCharsets.UTF_8).replace("+", "%20");
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                encodedIssuer, encodedAccount, secret, encodedIssuer, CODE_DIGITS, TIME_STEP_SECONDS);
    }

    /**
     * Generates the 6-digit TOTP code for a given secret at a specific Unix epoch timestamp.
     */
    public String generateCode(String secretBase32, long epochSeconds) {
        long timeStep = epochSeconds / TIME_STEP_SECONDS;
        return generateCodeForStep(secretBase32, timeStep);
    }

    /**
     * Verifies a 6-digit code against the secret within a ±1 time-step window (90s total window).
     *
     * @param secretBase32 Plaintext Base32 TOTP secret
     * @param rawCode 6-digit string supplied by the user
     * @param epochSeconds Current Unix timestamp
     * @param lastUsedTimeStep The previous time-step authenticated by this user, if any
     * @return An {@link OptionalLong} containing the verified time-step if valid and not replayed,
     *     or empty if invalid/replayed.
     */
    public OptionalLong verifyCode(String secretBase32, String rawCode, long epochSeconds, Long lastUsedTimeStep) {
        if (secretBase32 == null || rawCode == null) {
            return OptionalLong.empty();
        }

        String cleanedCode = rawCode.trim().replaceAll("\\s+", "");
        if (cleanedCode.length() != CODE_DIGITS || !cleanedCode.matches("\\d{6}")) {
            return OptionalLong.empty();
        }

        long currentStep = epochSeconds / TIME_STEP_SECONDS;

        // Check window: [currentStep - 1, currentStep, currentStep + 1]
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            if (lastUsedTimeStep != null && step <= lastUsedTimeStep) {
                // Prevent replay of codes from already consumed or older steps
                continue;
            }

            String expectedCode = generateCodeForStep(secretBase32, step);
            if (cleanedCode.equals(expectedCode)) {
                return OptionalLong.of(step);
            }
        }

        return OptionalLong.empty();
    }

    private String generateCodeForStep(String secretBase32, long timeStep) {
        try {
            byte[] keyBytes = decodeBase32(secretBase32);
            byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data);

            // Dynamic truncation per RFC 4226 / RFC 6238
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % MODULO;
            return String.format("%06d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate TOTP code", e);
        }
    }

    /**
     * Encodes raw binary bytes to Base32 string (RFC 4648, unpadded).
     */
    public static String encodeBase32(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                result.append(BASE32_ALPHABET.charAt(index));
            }
        }

        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(BASE32_ALPHABET.charAt(index));
        }

        return result.toString();
    }

    /**
     * Decodes a Base32 string to raw binary bytes (case-insensitive, strips spaces/hyphens).
     */
    public static byte[] decodeBase32(String base32) {
        String clean = base32.trim().toUpperCase().replaceAll("[\\s-]+", "").replace("=", "");
        ByteBuffer out = ByteBuffer.allocate((clean.length() * 5) / 8 + 1);

        int buffer = 0;
        int bitsLeft = 0;

        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Illegal character in Base32 string: " + c);
            }

            buffer = (buffer << 5) | val;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                out.put((byte) ((buffer >> (bitsLeft - 8)) & 0xFF));
                bitsLeft -= 8;
            }
        }

        return Arrays.copyOf(out.array(), out.position());
    }
}
