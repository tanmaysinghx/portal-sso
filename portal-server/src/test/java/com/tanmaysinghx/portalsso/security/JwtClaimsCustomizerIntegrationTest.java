package com.tanmaysinghx.portalsso.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.tanmaysinghx.portalsso.config.TestDataSeeder;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises the full authorization_code + PKCE flow through MockMvc against the seeded test
 * client, so a regression like a {@code LazyInitializationException} from {@link
 * JwtClaimsCustomizerConfig} reading {@code User.roles} outside of a transaction (caught once
 * already, since {@code open-in-view} is off) fails a build instead of only surfacing against a
 * running server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtClaimsCustomizerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @SuppressWarnings("unchecked")
    void authorizationCodeFlowIssuesTokensWithEmailAndRoleClaims() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("username", TestDataSeeder.TEST_USER_EMAIL)
                        .param("password", TestDataSeeder.TEST_USER_PASSWORD)
                        .with(csrf()))
                .andReturn();
        jakarta.servlet.http.Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).as("authenticated session cookie after login").isNotNull();

        String codeVerifier = "test-verifier-0123456789-0123456789-0123456789-abcdefghijk";
        String codeChallenge = codeChallenge(codeVerifier);

        // The authorization endpoint parses the raw query string (to detect duplicate
        // parameters), which MockHttpServletRequestBuilder#param() does not populate for GET
        // requests — the query string must be built into the request URI itself.
        URI authorizeUri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", TestDataSeeder.TEST_CLIENT_ID)
                .queryParam("redirect_uri", TestDataSeeder.TEST_CLIENT_REDIRECT_URI)
                .queryParam("scope", "openid profile email")
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", "xyz")
                .encode()
                .build()
                .toUri();

        MvcResult authorizeResult = mockMvc.perform(get(authorizeUri)
                        .cookie(sessionCookie))
                .andReturn();
        String location = authorizeResult.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).as("authorization redirect with code").isNotNull();
        String code = extractQueryParam(location, "code");

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", TestDataSeeder.TEST_CLIENT_REDIRECT_URI)
                        .param("client_id", TestDataSeeder.TEST_CLIENT_ID)
                        .param("code_verifier", codeVerifier))
                .andReturn();

        Map<String, Object> tokenResponse =
                jsonMapper.readValue(tokenResult.getResponse().getContentAsString(), Map.class);
        String idToken = (String) tokenResponse.get("id_token");
        assertThat(idToken).as("id_token in token response").isNotNull();

        Map<String, Object> claims = JWTParser.parse(idToken).getJWTClaimsSet().getClaims();
        assertThat(claims.get("email")).isEqualTo(TestDataSeeder.TEST_USER_EMAIL);
        assertThat((List<String>) claims.get("roles")).containsExactly("ROLE_USER");
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
