package com.tanmaysinghx.portalsso.security.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers the two server-rendered screens an end user meets during an OAuth2 flow.
 *
 * <p>The regression these guard against is subtle: naming a custom login page is what removes
 * Spring's {@code DefaultLoginPageGeneratingFilter}. Get the wiring wrong and {@code /login} stops
 * being served entirely, turning every sign-in into a redirect loop — which has already happened
 * once in this codebase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginServesTheBrandedPageRatherThanSpringsDefault() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Portal SSO")))
                // Spring's generated page is titled "Please sign in"; ours must have replaced it.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Please sign in"))));
    }

    /**
     * Exactly one CSRF field must be rendered. Thymeleaf injects one into any form with
     * {@code th:action}; adding a second by hand made the POST fail with 403 — verified live.
     */
    @Test
    void theLoginFormCarriesExactlyOneCsrfField() throws Exception {
        String html = mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        int occurrences = html.split("name=\"_csrf\"", -1).length - 1;
        org.assertj.core.api.Assertions.assertThat(occurrences)
                .as("a duplicated _csrf parameter is rejected by the CSRF filter")
                .isEqualTo(1);
    }

    @Test
    void anAuthenticationFailureIsSurfacedOnThePage() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", true));
    }

    @Test
    void signingOutIsAcknowledged() throws Exception {
        mockMvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loggedOut", true));
    }

    /**
     * Anonymous callers must not render a page that names a user and an application. The response
     * differs by what the caller accepts, and both forms deny access: a browser is redirected to
     * sign in, anything else gets a plain 401 rather than an HTML redirect it cannot use.
     */
    @Test
    void aBrowserIsSentToSignInRatherThanTheConsentScreen() throws Exception {
        mockMvc.perform(get("/oauth2/consent")
                        .header("Accept", "text/html,application/xhtml+xml")
                        .param("client_id", "test-client")
                        .param("scope", "openid profile")
                        .param("state", "xyz"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/login"));
    }

    @Test
    void aNonBrowserCallerIsRefusedOutright() throws Exception {
        mockMvc.perform(get("/oauth2/consent")
                        .param("client_id", "test-client")
                        .param("scope", "openid profile")
                        .param("state", "xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@portalsso.local")
    void theConsentScreenListsRequestedScopesButNeverOpenid() throws Exception {
        mockMvc.perform(get("/oauth2/consent")
                        .param("client_id", "test-client")
                        .param("scope", "openid profile email")
                        .param("state", "xyz"))
                .andExpect(status().isOk())
                .andExpect(view().name("consent"))
                // openid is implicit in an OIDC request and cannot meaningfully be declined.
                .andExpect(model().attribute("scopes", org.hamcrest.Matchers.hasSize(2)));
    }
}
