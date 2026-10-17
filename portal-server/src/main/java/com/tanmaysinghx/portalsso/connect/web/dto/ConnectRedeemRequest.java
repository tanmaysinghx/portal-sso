package com.tanmaysinghx.portalsso.connect.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param code the one-time code Portal appended to the app's callback URI
 * @param codeVerifier the secret whose S256 hash was submitted as {@code codeChallenge}
 */
public record ConnectRedeemRequest(
        @NotBlank @Size(max = 200) String code,
        @NotBlank @Size(min = 43, max = 128) String codeVerifier) {}
