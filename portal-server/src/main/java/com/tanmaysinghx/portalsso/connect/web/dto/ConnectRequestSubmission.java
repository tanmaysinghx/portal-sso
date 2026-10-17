package com.tanmaysinghx.portalsso.connect.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Submitted by the app's <em>backend</em> to {@code POST /api/external/connect/requests}.
 *
 * @param callbackUri where Portal sends the browser after the administrator decides. Must share
 *     the origin of {@code manifest.appUrl}; the one-time delivery code is appended to it.
 * @param codeChallenge base64url SHA-256 of a verifier only the app backend knows (RFC 7636 S256).
 */
public record ConnectRequestSubmission(
        @NotNull @Valid AppManifest manifest,
        @NotBlank @Size(max = 1000) String callbackUri,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "must be a base64url S256 challenge") String codeChallenge,
        @Pattern(regexp = "^S256$", message = "only S256 is supported") String codeChallengeMethod) {

    /**
     * What the app says about itself. Everything here is a request: the administrator sees it and
     * may withhold the sensitive parts (external auth API, registration) before approving.
     */
    public record AppManifest(
            @Size(max = 100) @Pattern(regexp = "^[a-z0-9._-]*$") String product,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotBlank @Size(max = 1000) String appUrl,
            @Size(max = 1000) String iconUrl,
            @Size(max = 100) @Pattern(regexp = "^[a-zA-Z0-9._-]*$") String clientId,
            @Size(max = 10) List<@Size(max = 500) String> redirectUris,
            @Size(max = 10) List<@Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9._:-]+$") String> scopes,
            Boolean confidential,
            Boolean externalAuth,
            Boolean externalRegistration) {}
}
