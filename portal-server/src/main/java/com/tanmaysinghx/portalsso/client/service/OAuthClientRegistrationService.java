package com.tanmaysinghx.portalsso.client.service;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.client.web.dto.CreateOAuthClientRequest;
import com.tanmaysinghx.portalsso.client.web.dto.OAuthClientCreatedResponse;
import com.tanmaysinghx.portalsso.client.web.dto.OAuthClientResponse;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.ResourceConflictException;
import com.tanmaysinghx.portalsso.common.error.ResourceNotFoundException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers new OAuth clients. Shared by the OAuth client registry and the one-step application
 * onboarding flow so both paths produce identical, PKCE-enforcing registrations.
 *
 * <p>Registers both public (PKCE-only, no secret) and confidential (secret-bearing) clients. Public
 * is the default and the right fit for SPAs and mobile apps, which cannot keep a secret; a
 * server-side web application asks for a confidential client and authenticates with
 * {@code client_secret_basic} or {@code client_secret_post}.
 */
@Service
public class OAuthClientRegistrationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OAuthClientRepository oAuthClientRepository;
    private final RegisteredClientRepository registeredClientRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    public OAuthClientRegistrationService(
            OAuthClientRepository oAuthClientRepository,
            RegisteredClientRepository registeredClientRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder) {
        this.oAuthClientRepository = oAuthClientRepository;
        this.registeredClientRepository = registeredClientRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    // Transactional so the registration, the logoUrl write and the audit entry commit together.
    // Without it a failure after registeredClientRepository.save() leaves a client that exists but
    // has no record of who created it — the one thing the audit trail is supposed to rule out.
    @Transactional
    public OAuthClientCreatedResponse create(CreateOAuthClientRequest request) {
        if (oAuthClientRepository.findByClientId(request.clientId()).isPresent()) {
            throw new ResourceConflictException(ErrorCode.CLIENT_ALREADY_EXISTS, "client_id already exists: " + request.clientId());
        }

        boolean confidential = Boolean.TRUE.equals(request.confidential());
        String rawSecret = confidential ? generateClientSecret() : null;

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(request.clientId())
                .clientName(request.clientName())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(request.redirectUris()))
                .scopes(scopes -> scopes.addAll(request.scopes()))
                .clientSettings(ClientSettings.builder()
                        // PKCE stays required even for confidential clients. OAuth 2.1 mandates it
                        // for every authorization_code client, and it defends against code
                        // interception independently of whether a secret is also presented. Relying
                        // apps must therefore send a code_challenge regardless of client type.
                        .requireProofKey(true)
                        .requireAuthorizationConsent(Boolean.TRUE.equals(request.requireConsent()))
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .reuseRefreshTokens(false)
                        .build());

        if (confidential) {
            // Hashed with the application's PasswordEncoder bean. Spring Authorization Server
            // resolves that same bean for client authentication, so the two agree without needing a
            // {id}-prefixed delegating encoder.
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .clientSecret(passwordEncoder.encode(rawSecret));
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }

        registeredClientRepository.save(builder.build());

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
                // The client TYPE is recorded, never the secret itself.
                "redirectUris=%s, scopes=%s, confidential=%s"
                        .formatted(saved.getRedirectUris(), saved.getScopes(), confidential));

        return new OAuthClientCreatedResponse(OAuthClientResponse.from(saved), rawSecret);
    }

    /**
     * 32 bytes from {@link SecureRandom}, URL-safe Base64 — 256 bits of entropy, so the secret is
     * not something an operator can weaken by choosing it themselves. It is returned once and
     * stored only as a hash; there is deliberately no endpoint that can show it again.
     */
    private static String generateClientSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
