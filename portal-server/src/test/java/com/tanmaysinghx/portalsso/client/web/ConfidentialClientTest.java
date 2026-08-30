package com.tanmaysinghx.portalsso.client.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfidentialClientTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private JsonNode create(String clientId, boolean confidential) throws Exception {
        String body = """
                {"clientId":"%s","clientName":"Test","redirectUris":["https://example.com/cb"],
                 "scopes":["openid"],"confidential":%s}
                """.formatted(clientId, confidential);
        return jsonMapper.readTree(mockMvc.perform(post("/api/admin/oauth-clients")
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void aPublicClientGetsNoSecretAndAuthenticatesWithNone() throws Exception {
        String id = "public-" + System.nanoTime();
        JsonNode response = create(id, false);

        assertThat(response.get("clientSecret").isNull()).isTrue();
        assertThat(response.get("client").get("confidential").asBoolean()).isFalse();
        assertThat(clientRepository.findByClientId(id).orElseThrow().getClientAuthenticationMethods())
                .isEqualTo("none");
    }

    @Test
    void aConfidentialClientGetsASecretReturnedExactlyOnce() throws Exception {
        String id = "confidential-" + System.nanoTime();
        JsonNode response = create(id, true);

        String secret = response.get("clientSecret").asString();
        assertThat(secret).isNotBlank();
        assertThat(response.get("client").get("confidential").asBoolean()).isTrue();

        // The list endpoint reuses a DTO that has no secret field at all, so it cannot leak one.
        mockMvc.perform(get("/api/admin/oauth-clients")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .param("search", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].confidential").value(true))
                .andExpect(jsonPath("$.content[0].clientSecret").doesNotExist());
    }

    /**
     * The stored value must be a hash, not the secret. A database read otherwise hands over working
     * client credentials — the same failure mode as storing a password in plaintext.
     */
    @Test
    void theSecretIsStoredOnlyAsAHash() throws Exception {
        String id = "hashed-" + System.nanoTime();
        String secret = create(id, true).get("clientSecret").asString();

        String stored = clientRepository.findByClientId(id).orElseThrow().getClientSecret();
        assertThat(stored).isNotNull().isNotEqualTo(secret);
        assertThat(passwordEncoder.matches(secret, stored))
                .as("hashed with the same PasswordEncoder bean the token endpoint verifies against")
                .isTrue();
    }

    @Test
    void aConfidentialClientAcceptsSecretBasicAndSecretPost() throws Exception {
        String id = "methods-" + System.nanoTime();
        create(id, true);

        String methods = clientRepository.findByClientId(id).orElseThrow().getClientAuthenticationMethods();
        assertThat(methods).contains("client_secret_basic").contains("client_secret_post");
    }

    /** Public remains the default: omitting the flag must not silently mint a secret-bearing client. */
    @Test
    void omittingTheFlagProducesAPublicClient() throws Exception {
        String id = "default-" + System.nanoTime();
        String body = """
                {"clientId":"%s","clientName":"Test","redirectUris":["https://example.com/cb"],"scopes":["openid"]}
                """.formatted(id);

        JsonNode response = jsonMapper.readTree(mockMvc.perform(post("/api/admin/oauth-clients")
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(response.get("clientSecret").isNull()).isTrue();
        assertThat(response.get("client").get("confidential").asBoolean()).isFalse();
    }

    /**
     * PKCE stays mandatory for confidential clients too. OAuth 2.1 requires it for every
     * authorization_code client, so relying apps must send a code_challenge regardless of type.
     */
    @Test
    void pkceIsRequiredForConfidentialClientsAsWell() throws Exception {
        String id = "pkce-" + System.nanoTime();
        create(id, true);

        assertThat(clientRepository.findByClientId(id).orElseThrow().getClientSettings())
                .contains("\"settings.client.require-proof-key\":true");
    }
}
