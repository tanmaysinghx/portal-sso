package com.tanmaysinghx.portalsso.client.security;

import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson.OAuth2AuthorizationServerJacksonModule;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link RegisteredClientRepository} backed by {@link OAuthClientRepository}. Column shape and
 * JSON encoding of {@code ClientSettings}/{@code TokenSettings} mirror Spring Authorization
 * Server's own {@code JdbcRegisteredClientRepository}, so this is a drop-in replacement of the
 * JDBC implementation with our JPA entity underneath (used later by the admin dashboard's client
 * registration screen).
 *
 * <p>PKCE is required for every client regardless of what is stored, since it is a platform-wide
 * invariant rather than a per-client toggle.
 */
@Component
public class JpaRegisteredClientRepository implements RegisteredClientRepository {

    private final OAuthClientRepository oAuthClientRepository;
    private final JsonMapper jsonMapper;

    public JpaRegisteredClientRepository(OAuthClientRepository oAuthClientRepository) {
        this.oAuthClientRepository = oAuthClientRepository;
        this.jsonMapper = createJsonMapper();
    }

    @Override
    @Transactional
    public void save(RegisteredClient registeredClient) {
        OAuthClient entity = oAuthClientRepository
                .findByClientId(registeredClient.getClientId())
                .orElseGet(() -> new OAuthClient(
                        registeredClient.getClientId(),
                        registeredClient.getClientName(),
                        joinValues(registeredClient.getClientAuthenticationMethods(), ClientAuthenticationMethod::getValue),
                        joinValues(registeredClient.getAuthorizationGrantTypes(), AuthorizationGrantType::getValue),
                        String.join(",", registeredClient.getScopes()),
                        jsonMapper.writeValueAsString(registeredClient.getClientSettings().getSettings()),
                        jsonMapper.writeValueAsString(registeredClient.getTokenSettings().getSettings())));

        entity.setClientSecret(registeredClient.getClientSecret());
        entity.setClientSecretExpiresAt(registeredClient.getClientSecretExpiresAt());
        entity.setClientName(registeredClient.getClientName());
        entity.setClientAuthenticationMethods(joinValues(registeredClient.getClientAuthenticationMethods(), ClientAuthenticationMethod::getValue));
        entity.setAuthorizationGrantTypes(joinValues(registeredClient.getAuthorizationGrantTypes(), AuthorizationGrantType::getValue));
        entity.setRedirectUris(String.join(",", registeredClient.getRedirectUris()));
        entity.setPostLogoutRedirectUris(String.join(",", registeredClient.getPostLogoutRedirectUris()));
        entity.setScopes(String.join(",", registeredClient.getScopes()));
        entity.setClientSettings(jsonMapper.writeValueAsString(registeredClient.getClientSettings().getSettings()));
        entity.setTokenSettings(jsonMapper.writeValueAsString(registeredClient.getTokenSettings().getSettings()));

        oAuthClientRepository.save(entity);
    }

    /**
     * Both lookups skip disabled clients, so Spring Authorization Server behaves exactly as if the
     * client were never registered: the authorization and token endpoints reject it.
     *
     * <p>Previously {@code enabled} was stored and displayed but never consulted, which meant a
     * client shown as "Disabled" in the admin console could still complete a full OAuth2 flow — a
     * control that looked like it worked and didn't. Filtering in {@code findById} as well as
     * {@code findByClientId} matters: the former is how the server resolves the client behind an
     * already-issued authorization, so disabling also stops existing refresh tokens.
     */
    @Override
    public RegisteredClient findById(String id) {
        return oAuthClientRepository.findById(UUID.fromString(id))
                .filter(OAuthClient::isEnabled)
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return oAuthClientRepository.findByClientId(clientId)
                .filter(OAuthClient::isEnabled)
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    private RegisteredClient toRegisteredClient(OAuthClient entity) {
        ClientSettings clientSettings = ClientSettings.withSettings(readSettings(entity.getClientSettings()))
                // PKCE is mandatory platform-wide; enforced here regardless of the stored value.
                .requireProofKey(true)
                .build();
        TokenSettings tokenSettings = TokenSettings.withSettings(readSettings(entity.getTokenSettings())).build();

        return RegisteredClient.withId(entity.getId().toString())
                .clientId(entity.getClientId())
                .clientIdIssuedAt(entity.getClientIdIssuedAt())
                .clientSecret(entity.getClientSecret())
                .clientSecretExpiresAt(entity.getClientSecretExpiresAt())
                .clientName(entity.getClientName())
                .clientAuthenticationMethods(methods -> methods.addAll(splitValues(entity.getClientAuthenticationMethods(), ClientAuthenticationMethod::new)))
                .authorizationGrantTypes(grants -> grants.addAll(splitValues(entity.getAuthorizationGrantTypes(), AuthorizationGrantType::new)))
                .redirectUris(uris -> uris.addAll(StringUtils.commaDelimitedListToSet(entity.getRedirectUris())))
                .postLogoutRedirectUris(uris -> uris.addAll(StringUtils.commaDelimitedListToSet(entity.getPostLogoutRedirectUris())))
                .scopes(scopes -> scopes.addAll(StringUtils.commaDelimitedListToSet(entity.getScopes())))
                .clientSettings(clientSettings)
                .tokenSettings(tokenSettings)
                .build();
    }

    private Map<String, Object> readSettings(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        return jsonMapper.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    private <T> String joinValues(Set<T> values, java.util.function.Function<T, String> toValue) {
        return values.stream().map(toValue).collect(Collectors.joining(","));
    }

    private <T> Set<T> splitValues(String csv, java.util.function.Function<String, T> fromValue) {
        return StringUtils.commaDelimitedListToSet(csv).stream().map(fromValue).collect(Collectors.toSet());
    }

    private static JsonMapper createJsonMapper() {
        ClassLoader classLoader = JpaRegisteredClientRepository.class.getClassLoader();
        return JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(classLoader))
                .addModule(new OAuth2AuthorizationServerJacksonModule())
                .build();
    }
}
