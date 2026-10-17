package com.tanmaysinghx.portalsso.application.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One-step onboarding: the catalog entry plus, optionally, a brand-new OAuth client to link to it.
 * Everything is created in a single transaction, so a failure part-way leaves nothing behind.
 *
 * <p>To link an <em>existing</em> client instead, leave {@code oauthClient} null and set
 * {@code application.clientId}. Setting both is rejected.
 */
public record OnboardApplicationRequest(
        @NotNull @Valid CreateApplicationRequest application,
        @Valid NewOAuthClient oauthClient) {

    /**
     * Every field is optional; the server fills sensible defaults so the common case is just
     * "create a client for this app".
     *
     * @param clientId defaults to a slug of the application name
     * @param redirectUris defaults to the launch URL; the SDK popup callback is always added
     * @param scopes defaults to {@code openid profile email}; {@code openid} is always added
     * @param confidential true issues a client secret (server-side apps only)
     */
    public record NewOAuthClient(
            @Size(max = 100) @Pattern(regexp = "^[a-zA-Z0-9._-]*$", message = "must be alphanumeric with '.', '_', '-'")
                    String clientId,
            List<@Size(max = 500) String> redirectUris,
            List<@Size(max = 100) String> scopes,
            Boolean confidential,
            Boolean requireConsent) {}
}
