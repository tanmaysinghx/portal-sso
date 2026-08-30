package com.tanmaysinghx.portalsso.client.web;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private final AuditService auditService;

    public AdminOAuthClientController(
            OAuthClientRepository oAuthClientRepository,
            RegisteredClientRepository registeredClientRepository,
            OAuth2GrantRevoker grantRevoker,
            AuditService auditService) {
        this.oAuthClientRepository = oAuthClientRepository;
        this.registeredClientRepository = registeredClientRepository;
        this.grantRevoker = grantRevoker;
        this.auditService = auditService;
    }

    @GetMapping
    public List<OAuthClientResponse> list() {
        return oAuthClientRepository.findAll().stream().map(OAuthClientResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // Transactional so the registration, the logoUrl write and the audit entry commit together.
    // Without it a failure after registeredClientRepository.save() leaves a client that exists but
    // has no record of who created it — the one thing the audit trail is supposed to rule out.
    @Transactional
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
                        .requireAuthorizationConsent(Boolean.TRUE.equals(request.requireConsent()))
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

        // logoUrl has no representation on RegisteredClient, so it is written straight to the
        // entity after the registration round-trip rather than through the client settings JSON.
        if (request.logoUrl() != null && !request.logoUrl().isBlank()) {
            saved.setLogoUrl(request.logoUrl().trim());
            saved = oAuthClientRepository.save(saved);
        }

        // Redirect URIs are recorded verbatim: adding one is how a registered client is turned into
        // a token-exfiltration path, so "who added that URI" has to be answerable after the fact.
        auditService.record(
                AuditAction.CLIENT_CREATED,
                saved.getId(),
                saved.getClientId(),
                "redirectUris=%s, scopes=%s".formatted(saved.getRedirectUris(), saved.getScopes()));

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

        // Read before mutating: the entity is managed, so after the setters below the "before"
        // values are gone and the log could only say that something changed, not what.
        String changes = describeChanges(client, request);

        client.setClientName(request.clientName());
        client.setRedirectUris(String.join(",", request.redirectUris()));
        client.setScopes(String.join(",", request.scopes()));
        client.setEnabled(request.enabled());
        client.setLogoUrl(request.logoUrl() == null || request.logoUrl().isBlank() ? null : request.logoUrl().trim());
        client.setClientSettings(withConsent(client.getClientSettings(), Boolean.TRUE.equals(request.requireConsent())));

        OAuthClient saved = oAuthClientRepository.save(client);
        auditService.record(AuditAction.CLIENT_UPDATED, saved.getId(), saved.getClientId(), changes);

        return OAuthClientResponse.from(saved);
    }

    /**
     * A {@code field: old -> new} summary of the security-relevant fields only. Logging the whole
     * request on every edit would bury the one line that matters — an added redirect URI, or a
     * disabled client quietly re-enabled — under fields nobody is auditing.
     */
    private static String describeChanges(OAuthClient before, UpdateOAuthClientRequest request) {
        List<String> changes = new ArrayList<>();
        appendIfChanged(changes, "redirectUris", before.getRedirectUris(), String.join(",", request.redirectUris()));
        appendIfChanged(changes, "scopes", before.getScopes(), String.join(",", request.scopes()));
        appendIfChanged(changes, "enabled", String.valueOf(before.isEnabled()), String.valueOf(request.enabled()));
        appendIfChanged(changes, "clientName", before.getClientName(), request.clientName());
        return changes.isEmpty() ? "no security-relevant fields changed" : String.join("; ", changes);
    }

    private static void appendIfChanged(List<String> changes, String field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add("%s: %s -> %s".formatted(field, before, after));
        }
    }

    /**
     * Flips {@code require-authorization-consent} inside the stored settings JSON, leaving every
     * other setting untouched. Rebuilding the record through {@code RegisteredClientRepository}
     * would be the tidier-looking option but resets columns that type does not model — {@code
     * enabled} and {@code logoUrl} among them.
     */
    private static String withConsent(String settingsJson, boolean requireConsent) {
        String key = "\"settings.client.require-authorization-consent\":";
        if (settingsJson == null || !settingsJson.contains(key)) {
            return settingsJson;
        }
        return settingsJson
                .replace(key + "true", key + requireConsent)
                .replace(key + "false", key + requireConsent);
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

        int revokedGrants = grantRevoker.revokeAllFor(client.getId().toString());
        oAuthClientRepository.delete(client);

        // Recorded after the delete so it only lands if the delete did, but with details captured
        // beforehand — target_label is the whole reason this row is still readable once the client
        // row is gone.
        auditService.record(
                AuditAction.CLIENT_DELETED,
                client.getId(),
                client.getClientId(),
                "revokedGrants=%d, redirectUris=%s".formatted(revokedGrants, client.getRedirectUris()));
    }
}
