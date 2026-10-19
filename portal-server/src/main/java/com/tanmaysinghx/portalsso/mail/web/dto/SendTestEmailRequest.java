package com.tanmaysinghx.portalsso.mail.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sends one test message so an administrator can confirm a relay works.
 *
 * @param recipient where to send it. Required — defaulting to the signed-in administrator's own
 *     address would make it easy to mail a stranger by accident on a shared account.
 * @param settings the configuration to test. Optional: when present the test runs against these
 *     unsaved values, which is the point — a relay should be provable before it is committed and
 *     switched on. When absent the stored configuration is used. Either way nothing is saved
 *     except the outcome of the attempt.
 */
public record SendTestEmailRequest(
        @NotBlank(message = "recipient is required")
        @Email(message = "recipient must be a valid email address")
        @Size(max = 320)
        String recipient,

        @Valid UpdateSmtpSettingsRequest settings) {}
