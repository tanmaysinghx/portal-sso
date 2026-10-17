package com.tanmaysinghx.portalsso.connect.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.application.repository.ApplicationRepository;
import com.tanmaysinghx.portalsso.application.web.dto.ExternalAppLoginRequest;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.connect.entity.AppConnectStatus;
import com.tanmaysinghx.portalsso.connect.repository.AppConnectRequestRepository;
import com.tanmaysinghx.portalsso.registration.web.dto.RegisterRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppConnectFlowIntegrationTest {

    private static final String APP = "https://eggless.example.com";
    private static final String CALLBACK = APP + "/api/portal-connect/callback";

    @Autowired private MockMvc mockMvc;
    @Autowired private AppConnectRequestRepository connectRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private OAuthClientRepository oAuthClientRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        connectRepository.deleteAll();
        applicationRepository.deleteAll();
    }

    @Test
    void approvedAppReceivesWorkingCredentialsExactlyOnce() throws Exception {
        String verifier = verifier();
        Map<String, Object> manifest = manifest(APP);
        manifest.put("externalRegistration", true);
        String requestId = submit(manifest, CALLBACK, verifier)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalUrl", containsString("/connect/")))
                .andReturn().getResponse().getContentAsString();
        String id = jsonMapper.readTree(requestId).get("requestId").asString();

        mockMvc.perform(get("/connect/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://eggless.example.com")))
                .andExpect(content().string(containsString("Eggless Observability")))
                .andExpect(content().string(containsString("Approve and connect")));

        String code = approve(id, true, true);

        // Wrong verifier with the right code: the request was submitted by someone else.
        redeem(id, code, verifier()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-3017"));

        String body = redeem(id, code, verifier)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.credentials.oidc.clientId").value("eggless-observability"))
                .andExpect(jsonPath("$.credentials.oidc.clientSecret").isString())
                .andExpect(jsonPath("$.credentials.externalAuth.apiKey", startsWith("psso_app_")))
                .andReturn().getResponse().getContentAsString();
        JsonNode redeemed = jsonMapper.readTree(body);
        String secret = redeemed.get("credentials").get("oidc").get("clientSecret").asString();
        String apiKey = redeemed.get("credentials").get("externalAuth").get("apiKey").asString();
        String appId = redeemed.get("applicationId").asString();

        assertThat(passwordEncoder.matches(secret, oAuthClientRepository.findByClientId("eggless-observability").orElseThrow().getClientSecret()))
                .isTrue();
        mockMvc.perform(post("/api/external/apps/" + appId + "/register")
                        .header("X-Portal-App-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RegisterRequest("connected@example.com", "Str0ngPassw0rd", "Con", "Nected"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/external/apps/" + appId + "/login")
                        .header("X-Portal-App-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ExternalAppLoginRequest("connected@example.com", "Str0ngPassw0rd"))))
                .andExpect(status().isOk());

        // Single use.
        redeem(id, code, verifier).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/external/connect/requests/" + id))
                .andExpect(jsonPath("$.status").value("redeemed"));
    }

    @Test
    void verifierAloneIsUselessWithoutTheCodeDeliveredToTheApp() throws Exception {
        // An attacker submits a request claiming the real app's address and keeps the verifier.
        String verifier = verifier();
        String id = submittedId(manifest(APP), CALLBACK, verifier);
        approve(id, true); // code goes to the real app's callback, not to the attacker

        for (int i = 0; i < 5; i++) {
            redeem(id, "guessed-code-" + i, verifier).andExpect(status().isBadRequest());
        }
        assertThat(connectRepository.findById(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(AppConnectStatus.LOCKED);
    }

    @Test
    void callbackAndRedirectsMustStayOnTheAppsOrigin() throws Exception {
        submit(manifest(APP), "https://evil.example.net/steal", verifier())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-3012"));

        Map<String, Object> m = manifest(APP);
        m.put("redirectUris", List.of("https://evil.example.net/auth/callback"));
        submit(m, CALLBACK, verifier()).andExpect(status().isBadRequest());

        submit(manifest("http://eggless.example.com"), "http://eggless.example.com/cb", verifier())
                .andExpect(status().isBadRequest());
        // Loopback is fine over http for local development.
        submit(manifest("http://localhost:8081"), "http://localhost:8081/api/portal-connect/callback", verifier())
                .andExpect(status().isCreated());
    }

    @Test
    void onlyAdministratorsCanApprove() throws Exception {
        String id = submittedId(manifest(APP), CALLBACK, verifier());

        mockMvc.perform(get("/connect/" + id).with(user("someone@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("administrator must approve")));
        mockMvc.perform(post("/connect/" + id + "/approve").with(user("someone@example.com").roles("USER")).with(csrf()))
                .andExpect(status().isOk());
        assertThat(connectRepository.findById(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(AppConnectStatus.PENDING);

        mockMvc.perform(get("/connect/" + id).accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/connect/" + id + "/approve").with(admin())).andExpect(status().isForbidden()); // no CSRF token
    }

    @Test
    void pendingAndDeniedRequestsCannotBeRedeemed() throws Exception {
        String verifier = verifier();
        String id = submittedId(manifest(APP), CALLBACK, verifier);
        redeem(id, "x", verifier).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRTL-3015"));

        mockMvc.perform(post("/connect/" + id + "/deny").with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith(CALLBACK)))
                .andExpect(header().string("Location", containsString("error=access_denied")));
        redeem(id, "x", verifier).andExpect(status().isForbidden());
        assertThat(applicationRepository.count()).isZero();
    }

    @Test
    void adminCanWithholdTheExternalAuthApi() throws Exception {
        String verifier = verifier();
        String id = submittedId(manifest(APP), CALLBACK, verifier);
        String code = approve(id, false);

        redeem(id, code, verifier)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentials.oidc.clientSecret").isString())
                .andExpect(jsonPath("$.credentials.externalAuth").doesNotExist());
    }

    @Test
    void reinstallAtTheSameAddressReconnectsAndRotatesCredentials() throws Exception {
        String v1 = verifier();
        String first = submittedId(manifest(APP), CALLBACK, v1);
        String firstSecret = jsonMapper.readTree(redeem(first, approve(first, true), v1).andReturn().getResponse().getContentAsString())
                .get("credentials").get("oidc").get("clientSecret").asString();

        String v2 = verifier();
        String second = submittedId(manifest(APP), CALLBACK, v2);
        mockMvc.perform(get("/connect/" + second).with(admin()))
                .andExpect(content().string(containsString("replaces its credentials")));
        String body = redeem(second, approve(second, true), v2)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newSecret = jsonMapper.readTree(body).get("credentials").get("oidc").get("clientSecret").asString();

        assertThat(applicationRepository.count()).isEqualTo(1);
        String hash = oAuthClientRepository.findByClientId("eggless-observability").orElseThrow().getClientSecret();
        assertThat(passwordEncoder.matches(newSecret, hash)).isTrue();
        assertThat(passwordEncoder.matches(firstSecret, hash)).isFalse();
    }

    @Test
    void sameClientIdAtAnotherAddressGetsItsOwnClient() throws Exception {
        String v1 = verifier();
        String first = submittedId(manifest(APP), CALLBACK, v1);
        redeem(first, approve(first, true), v1).andExpect(status().isOk());

        String other = "https://eggless.other.example.org";
        String v2 = verifier();
        String second = submittedId(manifest(other), other + "/api/portal-connect/callback", v2);
        redeem(second, approve(second, true), v2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentials.oidc.clientId", startsWith("eggless-observability-")));
        assertThat(applicationRepository.count()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ helpers

    private String approve(String id, boolean allowExternalAuth) throws Exception {
        return approve(id, allowExternalAuth, false);
    }

    private String approve(String id, boolean allowExternalAuth, boolean allowExternalRegistration) throws Exception {
        String location = mockMvc.perform(post("/connect/" + id + "/approve")
                        .param("allowExternalAuth", String.valueOf(allowExternalAuth))
                        .param("allowExternalRegistration", String.valueOf(allowExternalRegistration))
                        .with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).contains("request_id=" + id);
        return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
    }

    private ResultActions submit(Map<String, Object> manifest, String callback, String verifier) throws Exception {
        Map<String, Object> body = Map.of(
                "manifest", manifest,
                "callbackUri", callback,
                "codeChallenge", s256(verifier),
                "codeChallengeMethod", "S256");
        return mockMvc.perform(post("/api/external/connect/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    private String submittedId(Map<String, Object> manifest, String callback, String verifier) throws Exception {
        String body = submit(manifest, callback, verifier).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get("requestId").asString();
    }

    private ResultActions redeem(String id, String code, String verifier) throws Exception {
        return mockMvc.perform(post("/api/external/connect/requests/" + id + "/redeem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("code", code, "codeVerifier", verifier))));
    }

    private static Map<String, Object> manifest(String appUrl) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("product", "eggless-observability");
        m.put("name", "Eggless Observability");
        m.put("appUrl", appUrl);
        m.put("redirectUris", List.of(appUrl + "/auth/callback"));
        m.put("confidential", true);
        m.put("externalAuth", true);
        return m;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user("admin@portalsso.local").roles("ADMIN");
    }

    private static String verifier() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String s256(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
