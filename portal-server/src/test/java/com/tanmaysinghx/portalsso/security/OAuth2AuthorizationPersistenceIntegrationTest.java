package com.tanmaysinghx.portalsso.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.config.TestDataSeeder;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuth2AuthorizationPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcOperations jdbcOperations;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @SuppressWarnings("unchecked")
    void authorizationCodeFlowPersistsInDatabaseAndRefreshTokenSurvivesAndExchanges() throws Exception {
        String clientId = "auth-persist-client-" + UUID.randomUUID();
        String clientSecret = "Secret123!";
        String redirectUri = "http://127.0.0.1:8080/authorized";

        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientName("Auth Persist Client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri)
                .scope("openid")
                .scope("profile")
                .scope("email")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .reuseRefreshTokens(false)
                        .build())
                .build();
        registeredClientRepository.save(registeredClient);

        // 1. Authenticate user via form login
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("username", TestDataSeeder.TEST_USER_EMAIL)
                        .param("password", TestDataSeeder.TEST_USER_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).as("SESSION cookie issued").isNotNull();

        // 2. Perform /oauth2/authorize with PKCE
        String codeVerifier = "persist-verifier-0123456789-0123456789-0123456789-abcdefghijk";
        String codeChallenge = codeChallenge(codeVerifier);

        URI authorizeUri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid profile email")
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", "persisted_state")
                .encode()
                .build()
                .toUri();

        MvcResult authorizeResult = mockMvc.perform(get(authorizeUri)
                        .cookie(sessionCookie))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = authorizeResult.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        String code = extractQueryParam(location, "code");

        // 3. Exchange code for access token and refresh token
        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(clientId, clientSecret))
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", redirectUri)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> tokenResponse =
                jsonMapper.readValue(tokenResult.getResponse().getContentAsString(), Map.class);
        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");

        assertThat(accessToken).isNotNull();
        assertThat(refreshToken).isNotNull();

        // 4. Verify that authorization record is saved in oauth2_authorization database table
        Integer count = jdbcOperations.queryForObject("SELECT count(*) FROM oauth2_authorization", Integer.class);
        assertThat(count).isGreaterThan(0);

        OAuth2Authorization authorizationByRefresh =
                authorizationService.findByToken(refreshToken, OAuth2TokenType.REFRESH_TOKEN);
        assertThat(authorizationByRefresh).as("Authorization persisted in DB").isNotNull();
        assertThat(authorizationByRefresh.getPrincipalName()).isEqualTo(TestDataSeeder.TEST_USER_EMAIL);

        // 5. Test refresh token exchange (grant_type=refresh_token)
        MvcResult refreshResult = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(clientId, clientSecret))
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> refreshResponse =
                jsonMapper.readValue(refreshResult.getResponse().getContentAsString(), Map.class);
        String newAccessToken = (String) refreshResponse.get("access_token");
        assertThat(newAccessToken).isNotNull();
        assertThat(newAccessToken).isNotEqualTo(accessToken);
    }

    private static String codeChallenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static String extractQueryParam(String location, String name) {
        for (String pair : URI.create(location).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return kv[1];
            }
        }
        throw new IllegalStateException("Missing query param '" + name + "' in redirect: " + location);
    }
}
