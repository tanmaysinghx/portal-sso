package com.tanmaysinghx.portalsso.client.web.dto;

import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import java.time.Instant;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Never carries {@code clientSecret}. A confidential client's secret is shown once at creation (see
 * {@code OAuthClientCreatedResponse}) and stored only as a hash, so there is nothing to return here
 * even if this type wanted to.
 */
public record OAuthClientResponse(
        String id,
        String clientId,
        String clientName,
        List<String> redirectUris,
        List<String> scopes,
        boolean enabled,
        String logoUrl,
        boolean requireConsent,
        /** True when the client authenticates with a secret rather than PKCE alone. */
        boolean confidential,
        Instant createdAt) {

    /**
     * {@code requireAuthorizationConsent} lives inside the JSON-serialised client settings rather
     * than as its own column, so it is read back out by key instead of via a getter.
     */
    private static boolean readRequireConsent(OAuthClient entity) {
        String settings = entity.getClientSettings();
        return settings != null && settings.contains("\"settings.client.require-authorization-consent\":true");
    }

    /**
     * Read from the authentication methods rather than from whether a secret column is populated:
     * the method is what the token endpoint actually enforces, so it is the honest answer.
     */
    private static boolean isConfidential(OAuthClient entity) {
        String methods = entity.getClientAuthenticationMethods();
        return methods != null && methods.contains("client_secret");
    }

    public static OAuthClientResponse from(OAuthClient entity) {
        return new OAuthClientResponse(
                entity.getId().toString(),
                entity.getClientId(),
                entity.getClientName(),
                List.copyOf(StringUtils.commaDelimitedListToSet(entity.getRedirectUris())),
                List.copyOf(StringUtils.commaDelimitedListToSet(entity.getScopes())),
                entity.isEnabled(),
                entity.getLogoUrl(),
                readRequireConsent(entity),
                isConfidential(entity),
                entity.getCreatedAt());
    }
}
