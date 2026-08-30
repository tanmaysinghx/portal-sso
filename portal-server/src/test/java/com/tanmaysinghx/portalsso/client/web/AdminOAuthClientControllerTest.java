package com.tanmaysinghx.portalsso.client.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminOAuthClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthClientRepository oAuthClientRepository;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    private String createClient(String clientId) throws Exception {
        mockMvc.perform(post("/api/admin/oauth-clients")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"clientId":"%s","clientName":"Original Name",
                                 "redirectUris":["https://example.com/cb"],
                                 "scopes":["openid","email"]}
                                """
                                        .formatted(clientId)))
                .andExpect(status().isCreated());
        return oAuthClientRepository.findByClientId(clientId).orElseThrow().getId().toString();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateEditsMutableFieldsAndLeavesClientIdAlone() throws Exception {
        String clientId = "edit-me-" + UUID.randomUUID().toString().substring(0, 8);
        String id = createClient(clientId);

        mockMvc.perform(put("/api/admin/oauth-clients/" + id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"clientName":"Renamed",
                                 "redirectUris":["https://fixed.example.com/callback"],
                                 "scopes":["openid","profile"],
                                 "enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("Renamed"))
                // The identifier every relying app is configured with must survive an edit.
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andExpect(jsonPath("$.redirectUris[0]").value("https://fixed.example.com/callback"));
    }

    /**
     * The reason this endpoint exists: a mistyped redirect URI used to be unfixable without direct
     * database access.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void aMistypedRedirectUriCanBeCorrected() throws Exception {
        String clientId = "typo-" + UUID.randomUUID().toString().substring(0, 8);
        String id = createClient(clientId);

        mockMvc.perform(put("/api/admin/oauth-clients/" + id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"clientName":"Original Name",
                         "redirectUris":["https://corrected.example.com/callback"],
                         "scopes":["openid","email"],
                         "enabled":true}
                        """));

        OAuthClient stored = oAuthClientRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(stored.getRedirectUris()).isEqualTo("https://corrected.example.com/callback");
    }

    /**
     * {@code enabled} was stored and displayed but never consulted, so a client shown as "Disabled"
     * could still complete a full OAuth2 flow. Disabling must make the authorization server behave
     * as though the client does not exist.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void disablingAClientHidesItFromTheAuthorizationServer() throws Exception {
        String clientId = "disable-me-" + UUID.randomUUID().toString().substring(0, 8);
        String id = createClient(clientId);

        assertThat(registeredClientRepository.findByClientId(clientId))
                .as("precondition: an enabled client resolves")
                .isNotNull();

        mockMvc.perform(put("/api/admin/oauth-clients/" + id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"clientName":"Original Name",
                                 "redirectUris":["https://example.com/cb"],
                                 "scopes":["openid","email"],
                                 "enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(registeredClientRepository.findByClientId(clientId))
                .as("a disabled client must not resolve at the token/authorize endpoints")
                .isNull();
        assertThat(registeredClientRepository.findById(id))
                .as("nor when resolving the client behind an existing authorization")
                .isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteRemovesTheClient() throws Exception {
        String clientId = "delete-me-" + UUID.randomUUID().toString().substring(0, 8);
        String id = createClient(clientId);

        mockMvc.perform(delete("/api/admin/oauth-clients/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(oAuthClientRepository.findByClientId(clientId)).isEmpty();
        assertThat(registeredClientRepository.findByClientId(clientId)).isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatingOrDeletingAnUnknownClientIsNotFound() throws Exception {
        String missing = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/admin/oauth-clients/" + missing).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/admin/oauth-clients/" + missing)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"clientName":"X","redirectUris":["https://x.example.com/cb"],
                                 "scopes":["openid"],"enabled":true}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminsCannotEditOrDeleteClients() throws Exception {
        String missing = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/admin/oauth-clients/" + missing).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/oauth-clients/" + missing)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"clientName":"X","redirectUris":["https://x.example.com/cb"],
                                 "scopes":["openid"],"enabled":true}
                                """))
                .andExpect(status().isForbidden());
    }
}
