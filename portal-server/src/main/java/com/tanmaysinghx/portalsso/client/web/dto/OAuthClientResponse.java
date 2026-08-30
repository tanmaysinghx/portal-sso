package com.tanmaysinghx.portalsso.client.web.dto;

import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import java.time.Instant;
import java.util.List;
import org.springframework.util.StringUtils;

/** Never carries {@code clientSecret} — clients registered via the admin API are PKCE-only public clients. */
public record OAuthClientResponse(
        String id,
        String clientId,
        String clientName,
        List<String> redirectUris,
        List<String> scopes,
        boolean enabled,
        String logoUrl,
        boolean requireConsent,
        Instant createdAt) {

    /**
     * {@code requireAuthorizationConsent} lives inside the JSON-serialised client settings rather
     * than as its own column, so it is read back out by key instead of via a getter.
     */
    private static boolean readRequireConsent(OAuthClient entity) {
        String settings = entity.getClientSettings();
        return settings != null && settings.contains("\"settings.client.require-authorization-consent\":true");
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
                entity.getCreatedAt());
    }
}
