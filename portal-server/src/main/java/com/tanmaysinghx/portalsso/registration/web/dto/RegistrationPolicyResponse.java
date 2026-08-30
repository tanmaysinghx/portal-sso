package com.tanmaysinghx.portalsso.registration.web.dto;

import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;

/**
 * What an anonymous caller may know about this server's sign-up rules: whether registration is open,
 * whether new accounts wait for approval, and the password requirements.
 *
 * <p>The password rules are deliberately public. They are not a secret — every rejected attempt
 * reveals them anyway — and stating them up front is the difference between a usable sign-up form
 * and a guessing game.
 */
public record RegistrationPolicyResponse(
        boolean enabled,
        boolean requiresApproval,
        PasswordPolicy.PasswordPolicyDescription passwordPolicy) {}
