package com.tanmaysinghx.portalsso.mail.service;

import com.tanmaysinghx.portalsso.mail.entity.EmailSettings;

/**
 * Branding resolved to values a template can use without null checks.
 *
 * <p>Every field has a usable fallback, so an operator who configures a relay and nothing else
 * still gets correct, on-brand mail rather than a message with holes in it. The templates then
 * contain no defaulting logic of their own, which is what keeps the five of them consistent.
 *
 * @param portalUrl absolute base URL of this server, from the OIDC issuer. Links in mail have to be
 *     absolute and have to be reachable from outside, and the issuer is the one value already
 *     guaranteed to be both — it is what every relying application is configured against.
 */
public record EmailBranding(
        String productName,
        String logoUrl,
        String accentColor,
        String supportUrl,
        String footerNote,
        String portalUrl) {

    public static EmailBranding from(EmailSettings settings, String issuerUrl) {
        String portalUrl = stripTrailingSlash(issuerUrl);
        return new EmailBranding(
                orDefault(settings.getProductName(), EmailSettings.DEFAULT_PRODUCT_NAME),
                // No fallback image: a broken <img> looks worse than the wordmark the templates
                // render in its place, and an operator who has not set a logo has not asked for one.
                trimToNull(settings.getLogoUrl()),
                orDefault(settings.getAccentColor(), EmailSettings.DEFAULT_ACCENT_COLOR),
                trimToNull(settings.getSupportUrl()),
                trimToNull(settings.getFooterNote()),
                portalUrl);
    }

    public boolean hasLogo() {
        return logoUrl != null && !logoUrl.isBlank();
    }

    public boolean hasSupportUrl() {
        return supportUrl != null && !supportUrl.isBlank();
    }

    public boolean hasFooterNote() {
        return footerNote != null && !footerNote.isBlank();
    }

    private static String orDefault(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String trimmed = url.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
