package com.tanmaysinghx.portalsso.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.tanmaysinghx.portalsso.security.mfa.MfaEncryptionService;
import com.tanmaysinghx.portalsso.security.mfa.TotpService;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Password expiry at sign-in. The suite runs with the shipped default of 90 days, so a password is
 * made "expired" by backdating {@code password_changed_at} directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordExpiryIntegrationTest {

    private static final String EMAIL = "expiry-user@portalsso.local";
    private static final String OLD_PASSWORD = "ExpiringPassword123!";
    private static final String NEW_PASSWORD = "RenewedPassword456!";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TotpService totpService;
    @Autowired private MfaEncryptionService encryptionService;

    @BeforeEach
    void expiredUser() {
        User user = userRepository.findByEmail(EMAIL).orElseGet(() -> new User(EMAIL, passwordEncoder.encode(OLD_PASSWORD)));
        user.setPasswordHash(passwordEncoder.encode(OLD_PASSWORD));
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        setPasswordAge(100);
    }

    private void setPasswordAge(int days) {
        jdbcTemplate.update("update users set password_changed_at = ? where email = ?",
                Timestamp.from(Instant.now().minus(days, ChronoUnit.DAYS)), EMAIL);
    }

    private MvcResult signIn(boolean rememberMe) throws Exception {
        var request = post("/login").param("username", EMAIL).param("password", OLD_PASSWORD).with(csrf());
        if (rememberMe) {
            request = request.param("remember-me", "true");
        }
        return mockMvc.perform(request).andReturn();
    }

    @Test
    void aCurrentPasswordSignsInNormally() throws Exception {
        setPasswordAge(10);
        MvcResult login = signIn(false);
        assertThat(login.getResponse().getRedirectedUrl()).isNotEqualTo("/password-expired");
    }

    @Test
    void anExpiredPasswordDivertsAndGrantsNothingYet() throws Exception {
        MvcResult login = signIn(false);
        assertThat(login.getResponse().getRedirectedUrl()).isEqualTo("/password-expired");
        Cookie session = login.getResponse().getCookie("SESSION");

        mockMvc.perform(get("/api/admin/me").cookie(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/password-expired").cookie(session))
                .andExpect(status().isOk())
                .andExpect(view().name("password-expired"))
                .andExpect(model().attribute("email", EMAIL));
    }

    @Test
    void thePageIsUselessWithoutAPendingSignIn() throws Exception {
        mockMvc.perform(get("/password-expired")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/password-expired").with(csrf())
                        .param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/login"));
        assertThat(passwordEncoder.matches(OLD_PASSWORD, userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash()))
                .isTrue();
    }

    @Test
    void rejectsMismatchReuseAndPolicyFailuresOnThePage() throws Exception {
        Cookie session = signIn(false).getResponse().getCookie("SESSION");

        for (String[] attempt : new String[][] {
                {NEW_PASSWORD, "SomethingElse789!"}, {OLD_PASSWORD, OLD_PASSWORD}, {"short", "short"}}) {
            mockMvc.perform(post("/password-expired").cookie(session).with(csrf())
                            .param("newPassword", attempt[0]).param("confirmPassword", attempt[1]))
                    .andExpect(status().isOk())
                    .andExpect(view().name("password-expired"))
                    .andExpect(model().attributeExists("errorMessage"));
        }
        mockMvc.perform(get("/api/admin/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void changingItCompletesTheSignInAndRestartsTheClock() throws Exception {
        Cookie session = signIn(false).getResponse().getCookie("SESSION");

        MvcResult change = mockMvc.perform(post("/password-expired").cookie(session).with(csrf())
                        .param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(change.getResponse().getRedirectedUrl()).isEqualTo("/");

        // Completing the sign-in rotates the session id.
        Cookie rotated = change.getResponse().getCookie("SESSION");
        assertThat(rotated.getValue()).isNotEqualTo(session.getValue());
        mockMvc.perform(get("/api/admin/me").cookie(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/me").cookie(rotated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordChangedAt()).isAfter(Instant.now().minus(1, ChronoUnit.MINUTES));
    }

    @Test
    void anMfaAccountStillGetsItsChallengeAfterTheChange() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setMfaEnabled(true);
        user.setMfaSecret(encryptionService.encrypt(totpService.generateSecret()));
        userRepository.save(user);
        setPasswordAge(100);

        Cookie session = signIn(false).getResponse().getCookie("SESSION");
        mockMvc.perform(post("/password-expired").cookie(session).with(csrf())
                        .param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/mfa-challenge"));
        mockMvc.perform(get("/api/admin/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void rememberMeIsHeldBackUntilThePasswordIsChanged() throws Exception {
        MvcResult login = signIn(true);
        assertThat(login.getResponse().getCookie("remember-me")).isNull();

        Cookie session = login.getResponse().getCookie("SESSION");
        MvcResult change = mockMvc.perform(post("/password-expired").cookie(session).with(csrf())
                        .param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andReturn();
        Cookie issued = change.getResponse().getCookie("remember-me");
        assertThat(issued).isNotNull();
        assertThat(issued.getMaxAge()).isPositive();

        mockMvc.perform(get("/api/admin/me").cookie(issued))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void thePendingStepIsReportedToTheConsoleForItsOwnSessionOnly() throws Exception {
        mockMvc.perform(get("/api/public/sign-in/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").doesNotExist());

        Cookie session = signIn(false).getResponse().getCookie("SESSION");
        mockMvc.perform(get("/api/public/sign-in/pending").cookie(session))
                .andExpect(jsonPath("$.step").value("PASSWORD_EXPIRED"))
                .andExpect(jsonPath("$.path").value("/password-expired"));
    }
}
