package com.tanmaysinghx.portalsso.user.web.dto;

import com.tanmaysinghx.portalsso.security.password.PasswordPolicy.PasswordPolicyDescription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The signed-in user's own account, as the My Account page shows it.
 *
 * @param mfaAvailable false when the server has no at-rest encryption key, so enrolment would fail.
 * @param recoveryCodesRemaining unused recovery codes; zero when MFA is off.
 * @param passwordPolicy the rules a new password must meet, so the form can show them up front.
 */
public record AccountResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        List<String> roles,
        Instant createdAt,
        Instant lastLoginAt,
        boolean mfaEnabled,
        boolean mfaAvailable,
        int recoveryCodesRemaining,
        PasswordPolicyDescription passwordPolicy) {}
