package com.tanmaysinghx.portalsso.mail.web.dto;

import com.tanmaysinghx.portalsso.mail.entity.SmtpTransportSecurity;
import java.time.Instant;

/**
 * The stored configuration as the console sees it.
 *
 * <p>There is no password field, by design. The stored secret is never sent back — not even
 * masked with a fixed number of asterisks, which would leak its length. {@link #passwordSet} is
 * all the console needs to render "a password is stored" and to decide whether omitting the field
 * on save means "unchanged" or "none".
 *
 * @param usable whether a send would be attempted right now: enabled, with host, port and from
 *     address all present. The console shows this rather than {@code enabled} alone, so a
 *     half-filled configuration does not look live.
 */
public record SmtpSettingsResponse(
        boolean enabled,
        boolean usable,
        String host,
        Integer port,
        SmtpTransportSecurity transportSecurity,
        String username,
        boolean passwordSet,
        String fromAddress,
        String fromName,
        String replyToAddress,
        String updatedBy,
        Instant updatedAt,
        Instant lastTestAt,
        Boolean lastTestSuccessful,
        String lastTestError) {}
