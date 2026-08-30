package com.tanmaysinghx.portalsso.client.web;

import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.client.security.OAuth2GrantRevoker;
import com.tanmaysinghx.portalsso.client.web.dto.CreateOAuthClientRequest;
import com.tanmaysinghx.portalsso.client.web.dto.OAuthClientResponse;
import com.tanmaysinghx.portalsso.client.web.dto.UpdateOAuthClientRequest;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.ResourceConflictException;
import com.tanmaysinghx.portalsso.common.error.ResourceNotFoundException;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    private final OAuth2GrantRevoker grantRevoker;

    public AdminOAuthClientController(
            OAuthClientRepository oAuthClientRepository,
            RegisteredClientRepository registeredClientRepository,
            OAuth2GrantRevoker grantRevoker) {
        this.oAuthClientRepository = oAuthClientRepository;
        this.registeredClientRepository = registeredClientRepository;
        this.grantRevoker = grantRevoker;
    }

    @GetMapping
    public List<OAuthClientResponse> list() {
        return oAuthClientRepository.findAll().stream().map(OAuthClientResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OAuthClientResponse create(@Valid @RequestBody CreateOAuthClientRequest request) {
        if (oAuthClientRepository.findByClientId(request.clientId()).isPresent()) {
            throw new ResourceConflictException(ErrorCode.CLIENT_ALREADY_EXISTS, "client_id already exists: " + request.clientId());
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

        OAuthClient saved = oAuthClientRepository.findByClientId(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLIENT_NOT_FOUND, "OAuth client not found after saving: " + request.clientId()));
        return OAuthClientResponse.from(saved);
    }

    /**
     * Edits a client in place. Written through the JPA entity rather than
     * {@link RegisteredClientRepository#save} because that path rebuilds the whole record from a
     * {@link RegisteredClient}, which has no representation for our {@code enabled} column and
     * would reset it on every save.
     *
     * <p>{@code clientId} is intentionally not editable — see {@link UpdateOAuthClientRequest}.
     */
    @PutMapping("/{id}")
    @Transactional
    public OAuthClientResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateOAuthClientRequest request) {
        OAuthClient client = oAuthClientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLIENT_NOT_FOUND, "No OAuth client found with ID: " + id));

        client.setClientName(request.clientName());
        client.setRedirectUris(String.join(",", request.redirectUris()));
        client.setScopes(String.join(",", request.scopes()));
        client.setEnabled(request.enabled());

        return OAuthClientResponse.from(oAuthClientRepository.save(client));
    }

    /**
     * Deletes a client and revokes everything issued under it. The grants have to go explicitly:
     * the authorization tables reference the client by a plain column with no foreign key, so
     * nothing cascades — see {@link OAuth2GrantRevoker}.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable UUID id) {
        OAuthClient client = oAuthClientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLIENT_NOT_FOUND, "No OAuth client found with ID: " + id));

        grantRevoker.revokeAllFor(client.getId().toString());
        oAuthClientRepository.delete(client);
    }
}
