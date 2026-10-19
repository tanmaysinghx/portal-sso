package com.tanmaysinghx.portalsso.user.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RegenerateRecoveryCodesRequest(
        @NotBlank(message = "A code from your authenticator app is required") String code) {}
