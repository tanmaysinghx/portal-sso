package com.tanmaysinghx.portalsso.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.security.mfa.MfaEncryptionService;
import com.tanmaysinghx.portalsso.security.mfa.TotpService;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A password alone must not mint a remember-me cookie when sign-in has a second step still to go.
 * Spring issues the cookie before the success handler runs, so without the handler expiring it, the
 * cookie from an unfinished MFA sign-in is a full session that never saw the second factor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RememberMeSecondStepIntegrationTest {

    private static final String EMAIL = "remember-me-mfa@portalsso.local";
    private static final String PASSWORD = "RememberMeMfa123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TotpService totpService;
    @Autowired private MfaEncryptionService encryptionService;

    private String secret;

    @BeforeEach
    void mfaUser() {
        secret = totpService.generateSecret();
        User user = userRepository.findByEmail(EMAIL).orElseGet(() -> new User(EMAIL, passwordEncoder.encode(PASSWORD)));
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setMfaEnabled(true);
        user.setMfaSecret(encryptionService.encrypt(secret));
        user.setLastMfaTimeStep(null);
        userRepository.save(user);
    }

    @Test
    void rememberMeCookieFromAnUnfinishedMfaSignInDoesNotAuthenticate() throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", EMAIL)
                        .param("password", PASSWORD)
                        .param("remember-me", "true")
                        .with(csrf()))
                .andExpect(redirectedUrl("/mfa-challenge"))
                .andReturn();

        // Not issued at all — not even issued-then-cancelled, which would depend on the browser
        // honouring the later of two Set-Cookie headers.
        assertThat(login.getResponse().getCookie("remember-me")).isNull();
    }

    @Test
    void theCookieIsIssuedOnceTheSecondFactorIsVerified() throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", EMAIL)
                        .param("password", PASSWORD)
                        .param("remember-me", "true")
                        .with(csrf()))
                .andExpect(redirectedUrl("/mfa-challenge"))
                .andReturn();
        Cookie session = login.getResponse().getCookie("SESSION");

        String code = totpService.generateCode(secret, Instant.now().getEpochSecond());
        MvcResult verified = mockMvc.perform(post("/mfa-challenge").cookie(session).param("code", code).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Cookie rememberMe = verified.getResponse().getCookie("remember-me");
        assertThat(rememberMe).isNotNull();
        assertThat(rememberMe.getMaxAge()).isPositive();
        mockMvc.perform(get("/api/admin/me").cookie(rememberMe)).andExpect(status().isOk());
    }
}
