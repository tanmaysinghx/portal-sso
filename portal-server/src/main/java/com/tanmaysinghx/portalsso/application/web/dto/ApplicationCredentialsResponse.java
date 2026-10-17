package com.tanmaysinghx.portalsso.application.web.dto;

import java.util.List;

/**
 * Everything an application team needs to integrate with Portal SSO, in one object.
 *
 * <p>{@code clientSecret} and {@code apiKey} are populated <strong>only</strong> in the response
 * that created them (onboarding or key rotation). Both are stored as hashes, so every later read
 * returns null for them and {@code apiKeyPrefix} identifies which key is active.
 */
public record ApplicationCredentialsResponse(
        String issuerUrl,
        String discoveryUrl,
        Oidc oidc,
        ExternalAuth externalAuth) {

    /** Null when the application is not linked to an OAuth client. */
    public record Oidc(
            String clientId,
            String clientSecret,
            boolean confidential,
            List<String> redirectUris,
            List<String> scopes,
            String popupCallbackUri,
            boolean clientExists) {}

    /** Null when the external login/register API is disabled for the application. */
    public record ExternalAuth(
            String headerName,
            String apiKey,
            String apiKeyPrefix,
            boolean apiKeyIssued,
            boolean registrationEnabled,
            String loginEndpoint,
            String registerEndpoint) {}
}
