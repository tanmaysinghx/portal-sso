package com.tanmaysinghx.portalsso.mail.web.dto;

import com.tanmaysinghx.portalsso.mail.entity.EmailTemplateType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sends one template to an address, filled with representative sample data.
 *
 * <p>Deliberately ignores the notification switches: the point is to see what a message looks like
 * before deciding whether to turn it on, and having to enable a notification in order to preview it
 * would mean real users receive it during the review.
 */
public record SendTemplatePreviewRequest(
        @NotNull EmailTemplateType type,

        @NotBlank(message = "recipient is required")
        @Email(message = "recipient must be a valid email address")
        @Size(max = 320)
        String recipient) {}
