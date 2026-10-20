package com.tanmaysinghx.portalsso.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The emailed-credential primitives shared by magic links and password resets, kept in one place so
 * the two cannot drift apart.
 *
 * <p>Tokens are 256 bits of {@link SecureRandom}, URL-safe Base64. Only a SHA-256 of a token is
 * ever stored — a plain hash is enough because there is no low-entropy input to brute-force, unlike
 * a human-chosen password, so no slow KDF is needed.
 */
public final class OpaqueTokens {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private OpaqueTokens() {}

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hex SHA-256, the form tokens are stored and looked up in. */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * The lookup is already by indexed hash, so this does not decide which row is found. It keeps
     * the final accept/reject from short-circuiting on the first differing byte, so response timing
     * says nothing about how much of a hash was correct.
     */
    public static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
