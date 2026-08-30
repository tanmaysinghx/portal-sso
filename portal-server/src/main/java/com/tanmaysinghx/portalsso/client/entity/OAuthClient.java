package com.tanmaysinghx.portalsso.client.entity;

import com.tanmaysinghx.portalsso.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Registered OAuth2/OIDC client application. Column shape mirrors Spring Authorization Server's
 * own {@code oauth2_registered_client} reference schema (multi-valued fields such as scopes and
 * grant types are stored as comma-separated strings, and {@code clientSettings}/{@code
 * tokenSettings} as JSON) so this entity can back a {@code RegisteredClientRepository}
 * implementation directly.
 */
@Entity
@Table(
        name = "oauth_clients",
        uniqueConstraints = @UniqueConstraint(name = "uk_oauth_clients_client_id", columnNames = "client_id"))
public class OAuthClient extends BaseEntity {

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "client_id_issued_at", nullable = false)
    private Instant clientIdIssuedAt;

    @Column(name = "client_secret", length = 200)
    private String clientSecret;

    @Column(name = "client_secret_expires_at")
    private Instant clientSecretExpiresAt;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    /** Comma-separated, e.g. {@code "client_secret_basic,none"}. */
    @Column(name = "client_authentication_methods", nullable = false, length = 1000)
    private String clientAuthenticationMethods;

    /** Comma-separated, e.g. {@code "authorization_code,refresh_token"}. */
    @Column(name = "authorization_grant_types", nullable = false, length = 1000)
    private String authorizationGrantTypes;

    /** Comma-separated redirect URIs. */
    @Column(name = "redirect_uris", length = 1000)
    private String redirectUris;

    /** Comma-separated post-logout redirect URIs, used for OIDC front/back-channel logout. */
    @Column(name = "post_logout_redirect_uris", length = 1000)
    private String postLogoutRedirectUris;

    /** Comma-separated, e.g. {@code "openid,profile,email"}. */
    @Column(name = "scopes", nullable = false, length = 1000)
    private String scopes;

    /** JSON-serialized {@code ClientSettings} (PKCE requirement, consent requirement, etc.). */
    @Column(name = "client_settings", nullable = false, length = 2000)
    private String clientSettings;

    /** JSON-serialized {@code TokenSettings} (access/refresh token TTLs, reuse policy, etc.). */
    @Column(name = "token_settings", nullable = false, length = 2000)
    private String tokenSettings;

    /** Per-app branding shown on the login/consent screen. */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected OAuthClient() {
        // JPA
    }

    public OAuthClient(
            String clientId,
            String clientName,
            String clientAuthenticationMethods,
            String authorizationGrantTypes,
            String scopes,
            String clientSettings,
            String tokenSettings) {
        this.clientId = clientId;
        this.clientIdIssuedAt = Instant.now();
        this.clientName = clientName;
        this.clientAuthenticationMethods = clientAuthenticationMethods;
        this.authorizationGrantTypes = authorizationGrantTypes;
        this.scopes = scopes;
        this.clientSettings = clientSettings;
        this.tokenSettings = tokenSettings;
    }

    public String getClientId() {
        return clientId;
    }

    public Instant getClientIdIssuedAt() {
        return clientIdIssuedAt;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public Instant getClientSecretExpiresAt() {
        return clientSecretExpiresAt;
    }

    public void setClientSecretExpiresAt(Instant clientSecretExpiresAt) {
        this.clientSecretExpiresAt = clientSecretExpiresAt;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientAuthenticationMethods() {
        return clientAuthenticationMethods;
    }

    public void setClientAuthenticationMethods(String clientAuthenticationMethods) {
        this.clientAuthenticationMethods = clientAuthenticationMethods;
    }

    public String getAuthorizationGrantTypes() {
        return authorizationGrantTypes;
    }

    public void setAuthorizationGrantTypes(String authorizationGrantTypes) {
        this.authorizationGrantTypes = authorizationGrantTypes;
    }

    public String getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(String redirectUris) {
        this.redirectUris = redirectUris;
    }

    public String getPostLogoutRedirectUris() {
        return postLogoutRedirectUris;
    }

    public void setPostLogoutRedirectUris(String postLogoutRedirectUris) {
        this.postLogoutRedirectUris = postLogoutRedirectUris;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getClientSettings() {
        return clientSettings;
    }

    public void setClientSettings(String clientSettings) {
        this.clientSettings = clientSettings;
    }

    public String getTokenSettings() {
        return tokenSettings;
    }

    public void setTokenSettings(String tokenSettings) {
        this.tokenSettings = tokenSettings;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
