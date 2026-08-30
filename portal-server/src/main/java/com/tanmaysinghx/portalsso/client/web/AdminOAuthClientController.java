package com.tanmaysinghx.portalsso.client.web;

import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.client.web.dto.CreateOAuthClientRequest;
import com.tanmaysinghx.portalsso.client.web.dto.OAuthClientResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-only OAuth client registry. Registers PKCE-only public clients (no client secret) — the
 * fit for the portal's own SPA/mobile relying apps; confidential (secret-based) clients are a
 * deliberately deferred feature.
 */
@RestController
@RequestMapping("/api/admin/oauth-clients")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOAuthClientController {

    private final OAuthClientRepository oAuthClientRepository;
    private final RegisteredClientRepository registeredClientRepository;

    public AdminOAuthClientController(
            OAuthClientRepository oAuthClientRepository, RegisteredClientRepository registeredClientRepository) {
        this.oAuthClientRepository = oAuthClientRepository;
        this.registeredClientRepository = registeredClientRepository;
    }

    @GetMapping
    public List<OAuthClientResponse> list() {
        return oAuthClientRepository.findAll().stream().map(OAuthClientResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OAuthClientResponse create(@Valid @RequestBody CreateOAuthClientRequest request) {
        if (oAuthClientRepository.findByClientId(request.clientId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "client_id already exists: " + request.clientId());
        }

        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(request.clientId())
                .clientName(request.clientName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(request.redirectUris()))
                .scopes(scopes -> scopes.addAll(request.scopes()))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        registeredClientRepository.save(client);

        OAuthClient saved = oAuthClientRepository.findByClientId(request.clientId()).orElseThrow();
        return OAuthClientResponse.from(saved);
    }
}
