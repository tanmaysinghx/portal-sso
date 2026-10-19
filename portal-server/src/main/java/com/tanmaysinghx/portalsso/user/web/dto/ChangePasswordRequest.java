package com.tanmaysinghx.portalsso.user.web.dto;

import jakarta.validation.constraints.NotBlank;

/** The new password is judged by {@code PasswordPolicy}, not by annotations, so the rules live in one place. */
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required") String currentPassword,
        @NotBlank(message = "New password is required") String newPassword) {}
