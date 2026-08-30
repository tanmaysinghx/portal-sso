package com.tanmaysinghx.portalsso.client.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOAuthClientRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "must be alphanumeric with '.', '_', '-'")
                String clientId,
        @NotBlank @Size(max = 200) String clientName,
        @NotEmpty List<@NotBlank String> redirectUris,
        @NotEmpty List<@NotBlank String> scopes,

        /** Shown beside the Portal SSO mark on the sign-in and consent screens. Optional. */
        @Size(max = 500) String logoUrl,

        /** When true the user is asked to approve scopes on first authorization. */
        Boolean requireConsent) {}
