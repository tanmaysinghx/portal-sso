package com.tanmaysinghx.portalsso.mail.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Branding, per-notification switches and magic-link policy, as submitted from the console.
 *
 * @param accentColor a 3- or 6-digit hex colour. Validated by pattern rather than left free-form
 *     because it is interpolated straight into template markup — an unchecked value here would be
 *     a style-attribute injection into every message the server sends.
 * @param magicLinkTtlMinutes capped at 60. A sign-in link is a bearer credential sitting in a
 *     mailbox, so a long window is the one setting here that can quietly undo the feature's
 *     security; an hour is already generous for "check your email and click".
 */
public record UpdateEmailSettingsRequest(
        @Size(max = 100) String productName,

        @Size(max = 1000) String logoUrl,

        @Pattern(
                regexp = "^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$",
                message = "accentColor must be a hex colour such as #F59E0B")
        String accentColor,

        @Size(max = 1000) String supportUrl,

        @Size(max = 500) String footerNote,

        @NotNull Boolean welcomeEnabled,
        @NotNull Boolean newDeviceEnabled,
        @NotNull Boolean signInNoticeEnabled,
        @NotNull Boolean magicLinkEnabled,
        @NotNull Boolean magicLinkRegistrationEnabled,

        @NotNull
        @Min(value = 1, message = "magicLinkTtlMinutes must be between 1 and 60")
        @Max(value = 60, message = "magicLinkTtlMinutes must be between 1 and 60")
        Integer magicLinkTtlMinutes) {}
