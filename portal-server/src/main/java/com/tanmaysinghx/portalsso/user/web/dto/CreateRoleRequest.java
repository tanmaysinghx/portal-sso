package com.tanmaysinghx.portalsso.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The {@code ROLE_} prefix is enforced rather than suggested. A role's name becomes the granted
 * authority verbatim (see {@code PortalUserDetailsService}), and Spring's {@code hasRole('X')}
 * looks for the authority {@code ROLE_X}. A role named "EDITOR" would therefore be assignable,
 * visible in the console and present in the JWT, yet never satisfy {@code hasRole('EDITOR')} — a
 * permission that looks granted and silently is not.
 */
public record CreateRoleRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(
                regexp = "^ROLE_[A-Z0-9_]+$",
                message = "must start with ROLE_ and use upper-case letters, digits and underscores")
        String name,

        @Size(max = 255) String description) {}
