package com.tanmaysinghx.portalsso.application.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.application.entity.ApplicationAccessType;
import com.tanmaysinghx.portalsso.application.repository.ApplicationRepository;
import com.tanmaysinghx.portalsso.application.web.dto.CreateApplicationRequest;
import com.tanmaysinghx.portalsso.application.web.dto.ExternalAppLoginRequest;
import com.tanmaysinghx.portalsso.application.web.dto.OnboardApplicationRequest;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.registration.web.dto.RegisterRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationOnboardingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private OAuthClientRepository oAuthClientRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
    }

    @Test
    void onboardingWithDefaultsCreatesALinkedPublicClient() throws Exception {
        onboard(app("Grafana Dashboard", null, false), new OnboardApplicationRequest.NewOAuthClient(null, null, null, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.application.clientId").value("grafana-dashboard"))
                .andExpect(jsonPath("$.credentials.issuerUrl").isString())
                .andExpect(jsonPath("$.credentials.discoveryUrl").value(startsWith("http")))
                .andExpect(jsonPath("$.credentials.oidc.clientId").value("grafana-dashboard"))
                .andExpect(jsonPath("$.credentials.oidc.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.credentials.oidc.confidential").value(false))
                .andExpect(jsonPath("$.credentials.oidc.scopes", hasItems("openid", "profile", "email")))
                .andExpect(jsonPath("$.credentials.oidc.redirectUris", hasItems("https://grafana.internal")))
                .andExpect(jsonPath("$.credentials.externalAuth").doesNotExist());

        JsonNode creds = getCredentials("grafana-dashboard");
        String popup = creds.get("oidc").get("popupCallbackUri").asString();
        assertThat(popup).endsWith("/oauth2/popup-callback");
        assertThat(creds.get("oidc").get("redirectUris").toString()).contains(popup);
        assertThat(oAuthClientRepository.findByClientId("grafana-dashboard")).isPresent();
    }

    @Test
    void confidentialClientAndApiKeyAreReturnedOnceThenNeverAgain() throws Exception {
        String body = onboard(
                        app("Ticket Desk", null, true),
                        new OnboardApplicationRequest.NewOAuthClient("ticket-desk", List.of("https://tickets.example.com/callback"), List.of("openid", "email"), true, false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credentials.oidc.confidential").value(true))
                .andExpect(jsonPath("$.credentials.oidc.clientSecret").isString())
                .andExpect(jsonPath("$.credentials.externalAuth.apiKey").value(startsWith("psso_app_")))
                .andExpect(jsonPath("$.credentials.externalAuth.headerName").value("X-Portal-App-Key"))
                .andExpect(jsonPath("$.application.externalAuthApiKeyIssued").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode created = jsonMapper.readTree(body);
        String appId = created.get("application").get("id").asString();
        String apiKey = created.get("credentials").get("externalAuth").get("apiKey").asString();
        String loginEndpoint = created.get("credentials").get("externalAuth").get("loginEndpoint").asString();
        assertThat(loginEndpoint).startsWith("http").endsWith("/api/external/apps/" + appId + "/login");

        mockMvc.perform(get("/api/admin/applications/" + appId + "/credentials")
                        .with(user("admin@portalsso.local").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oidc.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.externalAuth.apiKey").doesNotExist())
                .andExpect(jsonPath("$.externalAuth.apiKeyPrefix").value(apiKey.substring(0, 12)))
                .andExpect(jsonPath("$.oidc.redirectUris", hasItems("https://tickets.example.com/callback")));

        // The key issued during onboarding is immediately usable by the app backend.
        mockMvc.perform(post("/api/external/apps/" + appId + "/register")
                        .header("X-Portal-App-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RegisterRequest("onboard@example.com", "Str0ngPassw0rd", "On", "Board"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/external/apps/" + appId + "/login")
                        .header("X-Portal-App-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ExternalAppLoginRequest("onboard@example.com", "Str0ngPassw0rd"))))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateClientIdIsRejectedAndNoApplicationIsCreated() throws Exception {
        onboard(app("First", null, false), new OnboardApplicationRequest.NewOAuthClient("shared-id", null, null, null, null))
                .andExpect(status().isCreated());

        onboard(app("Second", null, false), new OnboardApplicationRequest.NewOAuthClient("shared-id", null, null, null, null))
                .andExpect(status().isConflict());

        assertThat(applicationRepository.findAll()).extracting("name").containsExactly("First");
    }

    @Test
    void linkingAndCreatingAClientAtOnceIsRejected() throws Exception {
        onboard(app("Both", "some-client", false), new OnboardApplicationRequest.NewOAuthClient(null, null, null, null, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void linkingAnUnknownClientIdIsRejected() throws Exception {
        onboard(app("Typo", "does-not-exist", false), null)
                .andExpect(status().isNotFound());
        assertThat(applicationRepository.findAll()).isEmpty();
    }

    @Test
    void bookmarkOnlyApplicationHasNoOidcOrExternalAuthSettings() throws Exception {
        onboard(app("Wiki", null, false), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.application.clientId").doesNotExist())
                .andExpect(jsonPath("$.credentials.oidc").doesNotExist())
                .andExpect(jsonPath("$.credentials.externalAuth").doesNotExist());
    }

    private ResultActions onboard(CreateApplicationRequest app, OnboardApplicationRequest.NewOAuthClient client) throws Exception {
        return mockMvc.perform(post("/api/admin/applications/onboard")
                .with(user("admin@portalsso.local").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new OnboardApplicationRequest(app, client))));
    }

    private JsonNode getCredentials(String clientId) throws Exception {
        String appId = applicationRepository.findAll().stream()
                .filter(a -> clientId.equals(a.getClientId()))
                .findFirst().orElseThrow().getId().toString();
        String body = mockMvc.perform(get("/api/admin/applications/" + appId + "/credentials")
                        .with(user("admin@portalsso.local").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body);
    }

    private static CreateApplicationRequest app(String name, String clientId, boolean externalAuth) {
        return new CreateApplicationRequest(
                name,
                null,
                "https://grafana.internal",
                null,
                "General",
                clientId,
                ApplicationAccessType.ALL_USERS,
                List.of(),
                true,
                0,
                externalAuth,
                true);
    }
}
