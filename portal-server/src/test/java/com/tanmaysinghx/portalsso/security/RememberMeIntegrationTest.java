package com.tanmaysinghx.portalsso.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.config.TestDataSeeder;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RememberMeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginWithRememberMeSetsCookieAndAuthenticatesFutureRequestsWithoutSessionCookie() throws Exception {
        // 1. Log in with remember-me=true
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("username", TestDataSeeder.TEST_ADMIN_EMAIL)
                        .param("password", TestDataSeeder.TEST_ADMIN_PASSWORD)
                        .param("remember-me", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().exists("remember-me"))
                .andReturn();

        Cookie rememberMeCookie = loginResult.getResponse().getCookie("remember-me");
        assertThat(rememberMeCookie).isNotNull();
        assertThat(rememberMeCookie.getValue()).isNotEmpty();

        // 2. Perform a request to /api/admin/me supplying ONLY remember-me cookie (no SESSION cookie)
        mockMvc.perform(get("/api/admin/me")
                        .cookie(rememberMeCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TestDataSeeder.TEST_ADMIN_EMAIL))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
    }

    @Test
    void loginWithoutRememberMeDoesNotSetRememberMeCookie() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", TestDataSeeder.TEST_ADMIN_EMAIL)
                        .param("password", TestDataSeeder.TEST_ADMIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().doesNotExist("remember-me"));
    }
}
