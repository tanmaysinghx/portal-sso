package com.tanmaysinghx.portalsso.registration.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Note what is absent: no {@code roles} and no {@code enabled}. Both are decided by the server
 * from {@link com.tanmaysinghx.portalsso.registration.config.RegistrationProperties}. Accepting
 * either from an unauthenticated caller would let anyone mint themselves an enabled administrator.
 */
public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @Size(max = 100, message = "First name cannot exceed 100 characters")
        String firstName,

        @Size(max = 100, message = "Last name cannot exceed 100 characters")
        String lastName
) {}
