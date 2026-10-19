package com.tanmaysinghx.portalsso.registration.web.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateRegistrationSettingsRequest(
        @NotNull Boolean enabled,
        @NotNull Boolean requiresApproval) {}
