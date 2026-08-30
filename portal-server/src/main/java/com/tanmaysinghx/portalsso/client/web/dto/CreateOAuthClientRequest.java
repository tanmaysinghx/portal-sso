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
        Boolean requireConsent,

        /**
         * When true the client is issued a secret and authenticates with {@code client_secret_basic}
         * or {@code client_secret_post} — the shape a server-side web application needs, where the
         * secret can actually be kept.
         *
         * <p>Leave false for browser and mobile apps. A public client cannot hold a secret (anyone
         * with the bundle has it), which is why PKCE exists and why it is the default here.
         *
         * <p>The secret is returned <strong>once</strong>, in the create response, and stored only as
         * an Argon2 hash. There is no endpoint that can read it back.
         */
        Boolean confidential) {}
