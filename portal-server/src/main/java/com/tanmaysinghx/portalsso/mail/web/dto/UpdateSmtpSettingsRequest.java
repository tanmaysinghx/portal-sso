package com.tanmaysinghx.portalsso.mail.web.dto;

import com.tanmaysinghx.portalsso.mail.entity.SmtpTransportSecurity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The settings an administrator submits from the console.
 *
 * <p>Only {@code enabled} and {@code transportSecurity} are required at this layer. The fields a
 * working relay actually needs — host, port, from address — are validated by
 * {@code SmtpSettingsService} only when {@code enabled} is true, so a half-filled configuration can
 * be saved and come back on the next visit instead of being lost.
 *
 * @param password the relay password in plaintext, encrypted before it is stored. Leave
 *     {@code null} to keep the stored password unchanged — that is what the console sends when the
 *     administrator edits the port without retyping the secret. Send an empty string to clear it.
 */
public record UpdateSmtpSettingsRequest(
        @NotNull Boolean enabled,

        @Size(max = 255) String host,

        @Min(value = 1, message = "port must be between 1 and 65535")
        @Max(value = 65535, message = "port must be between 1 and 65535")
        Integer port,

        @NotNull SmtpTransportSecurity transportSecurity,

        @Size(max = 255) String username,

        @Size(max = 500) String password,

        @Email(message = "fromAddress must be a valid email address")
        @Size(max = 320)
        String fromAddress,

        @Size(max = 255) String fromName,

        @Email(message = "replyToAddress must be a valid email address")
        @Size(max = 320)
        String replyToAddress) {}
