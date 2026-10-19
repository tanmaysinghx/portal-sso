package com.tanmaysinghx.portalsso.security.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * A browser and operating system, reduced to something stable enough to recognise a device by.
 *
 * <h2>Why versions are deliberately thrown away</h2>
 *
 * <p>The obvious fingerprint is the whole {@code User-Agent} string, and it is the wrong one.
 * Chrome ships a new major version every four weeks and rewrites that string each time, so a
 * version-sensitive hash would declare a user's own laptop a brand-new device roughly monthly. A
 * security alert that cries wolf on a schedule is one people learn to delete unread, which costs
 * more than the precision gains. So the signature is browser family + OS family + form factor, and
 * nothing finer.
 *
 * <p>What that trades away is honest and worth stating: two people on Chrome and Windows look
 * identical to this, so a stolen session replayed from similar hardware raises no alert. This is a
 * change-of-device heuristic, not an identity check — IP and country travel alongside it in the
 * alert so the recipient can judge.
 *
 * @param browser browser family, never a version — "Chrome", "Safari", "Firefox".
 * @param os operating-system family, never a version — "Windows", "macOS", "iOS".
 * @param deviceType form factor, which separates a phone from a laptop on the same browser family.
 */
public record DeviceSignature(String browser, String os, String deviceType) {

    private static final String UNKNOWN = "Unknown";

    /** Parses a raw {@code User-Agent} header. A null or empty header yields an all-unknown signature. */
    public static DeviceSignature parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new DeviceSignature(UNKNOWN, UNKNOWN, UNKNOWN);
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        return new DeviceSignature(detectBrowser(ua), detectOs(ua), detectDeviceType(ua));
    }

    /**
     * Order matters throughout: every Chromium browser also claims "chrome", and Chrome claims
     * "safari", so the specific names have to be tested before the generic ones.
     */
    private static String detectBrowser(String ua) {
        if (ua.contains("edg/") || ua.contains("edge/")) {
            return "Microsoft Edge";
        }
        if (ua.contains("opr/") || ua.contains("opera")) {
            return "Opera";
        }
        if (ua.contains("vivaldi")) {
            return "Vivaldi";
        }
        if (ua.contains("brave")) {
            return "Brave";
        }
        if (ua.contains("samsungbrowser")) {
            return "Samsung Internet";
        }
        if (ua.contains("firefox") || ua.contains("fxios")) {
            return "Firefox";
        }
        if (ua.contains("crios")) {
            return "Chrome";
        }
        if (ua.contains("chrome") || ua.contains("chromium")) {
            return "Chrome";
        }
        if (ua.contains("safari")) {
            return "Safari";
        }
        if (ua.contains("postmanruntime")) {
            return "Postman";
        }
        if (ua.contains("curl")) {
            return "cURL";
        }
        return UNKNOWN;
    }

    private static String detectOs(String ua) {
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("iphone")) {
            return "iOS";
        }
        if (ua.contains("ipad")) {
            return "iPadOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        // Must follow the iOS checks: mobile Safari reports "like Mac OS X".
        if (ua.contains("mac os x") || ua.contains("macintosh")) {
            return "macOS";
        }
        if (ua.contains("cros")) {
            return "ChromeOS";
        }
        if (ua.contains("linux") || ua.contains("ubuntu") || ua.contains("fedora")) {
            return "Linux";
        }
        return UNKNOWN;
    }

    private static String detectDeviceType(String ua) {
        if (ua.contains("ipad") || ua.contains("tablet") || ua.contains("playbook")) {
            return "Tablet";
        }
        if (ua.contains("iphone") || ua.contains("ipod") || ua.contains("mobile")
                || ua.contains("phone") || ua.contains("blackberry")) {
            return "Mobile";
        }
        if (ua.contains("windows") || ua.contains("mac os x") || ua.contains("macintosh")
                || ua.contains("linux") || ua.contains("cros")) {
            return "Desktop";
        }
        return UNKNOWN;
    }

    /**
     * The stable identifier stored in {@code user_known_devices}.
     *
     * <p>Hashed rather than stored as plain text so the table does not accumulate a per-user
     * browsing profile in readable form. It is not a secret — the input space is tiny and trivially
     * enumerable — so this is tidiness about what a dump reveals at a glance, not a security
     * boundary, and it is worth being clear about which of the two it is.
     */
    public String hash() {
        String canonical = (browser + "|" + os + "|" + deviceType).toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK; its absence is not a condition to degrade for.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** What the recipient of an alert reads — "Chrome on macOS", "Safari on iOS (Mobile)". */
    public String label() {
        if (UNKNOWN.equals(browser) && UNKNOWN.equals(os)) {
            return "Unknown device";
        }
        String base = browser + " on " + os;
        return "Desktop".equals(deviceType) || UNKNOWN.equals(deviceType)
                ? base
                : base + " (" + deviceType + ")";
    }
}
