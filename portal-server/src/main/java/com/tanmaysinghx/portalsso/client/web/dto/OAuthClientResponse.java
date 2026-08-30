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
        Instant createdAt) {

    public static OAuthClientResponse from(OAuthClient entity) {
        return new OAuthClientResponse(
                entity.getId().toString(),
                entity.getClientId(),
                entity.getClientName(),
                List.copyOf(StringUtils.commaDelimitedListToSet(entity.getRedirectUris())),
                List.copyOf(StringUtils.commaDelimitedListToSet(entity.getScopes())),
                entity.isEnabled(),
                entity.getCreatedAt());
    }
}
