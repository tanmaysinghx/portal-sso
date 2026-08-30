package com.tanmaysinghx.portalsso.security.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.config.TestDataSeeder;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RecoveryCodeRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MfaChallengeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private MfaService mfaService;

    @Autowired
    private TotpService totpService;

    @Autowired
    private MfaEncryptionService encryptionService;

    @Value("${app.security.max-failed-login-attempts:5}")
    private int maxFailedAttempts;

    private static final String USER_EMAIL = TestDataSeeder.TEST_USER_EMAIL;
    private static final String USER_PASSWORD = TestDataSeeder.TEST_USER_PASSWORD;
    private static final String ADMIN_EMAIL = TestDataSeeder.TEST_ADMIN_EMAIL;
    private static final String ADMIN_PASSWORD = TestDataSeeder.TEST_ADMIN_PASSWORD;

    @BeforeEach
    @AfterEach
    void resetUserState() {
        userRepository.findByEmail(USER_EMAIL).ifPresent(user -> {
            user.setMfaEnabled(false);
            user.setMfaSecret(null);
            user.setLastMfaTimeStep(null);
            user.setAccountLocked(false);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
            recoveryCodeRepository.deleteByUser(user);
        });

        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(admin -> {
            admin.setMfaEnabled(false);
            admin.setMfaSecret(null);
            admin.setLastMfaTimeStep(null);
            admin.setAccountLocked(false);
            admin.setFailedLoginAttempts(0);
            userRepository.save(admin);
            recoveryCodeRepository.deleteByUser(admin);
        });
    }

    @Test
    void userWithoutMfaLogsInDirectly() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", USER_EMAIL)
                        .param("password", USER_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void userWithMfaRedirectsToChallengeAndCannotAccessApiWhileHalfAuthenticated() throws Exception {
        // Enable MFA for user
        User user = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        mfaService.initiateSetup(user);
        String secret = encryptionService.decrypt(user.getMfaSecret());
        String code = totpService.generateCode(secret, Instant.now().getEpochSecond());
        mfaService.confirmSetup(user, code);

        // 1. Password sign-in redirects to /mfa-challenge
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("username", USER_EMAIL)
                        .param("password", USER_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mfa-challenge"))
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        // 2. Half-authenticated session attempting to access /api/** must get 401 Unauthorized
        mockMvc.perform(get("/api/admin/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/mfa/status").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mfaChallengePageRequiresPreAuthSessionAndHasExactlyOneCsrfField() throws Exception {
        // Without pre-auth session -> redirect to /login
        mockMvc.perform(get("/mfa-challenge"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        // Enable MFA and start login
        User user = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        mfaService.initiateSetup(user);
        String secret = encryptionService.decrypt(user.getMfaSecret());
        String code = totpService.generateCode(secret, Instant.now().getEpochSecond());
        mfaService.confirmSetup(user, code);

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("username", USER_EMAIL)
                        .param("password", USER_PASSWORD)
                        .with(csrf()))
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        // With pre-auth session -> renders challenge page
        MvcResult pageResult = mockMvc.perform(get("/mfa-challenge").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn();

        String html = pageResult.getResponse().getContentAsString();
        assertThat(html).contains("Two-factor authentication");

        // Assert exactly one _csrf field in form
        Matcher matcher = Pattern.compile("name=[\"']_csrf[\"']").matcher(html);
        int csrfCount = 0;
        while (matcher.find()) {
            csrfCount++;
        }
        assertThat(csrfCount).as("Must contain exactly one _csrf input to prevent 403 errors").isEqualTo(1);
    }

    @Test
    void validTotpCodeCompletesAuthentication() throws Exception {
        User user = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        mfaService.initiateSetup(user);
        String secret = encryptionService.decrypt(user.getMfaSecret());
        String initialCode = totpService.generateCode(secret, Instant.now().getEpochSecond());
        mfaService.confirmSetup(user, initialCode);
        user.setLastMfaTimeStep(null);
        userRepository.save(user);

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("username", USER_EMAIL)
                        .param("password", USER_PASSWORD)
                        .with(csrf()))
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        long now = Instant.now().getEpochSecond();
        String validCode = totpService.generateCode(secret, now);

        mockMvc.perform(post("/mfa-challenge")
                        .cookie(sessionCookie)
                        .param("code", validCode)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Now fully authenticated: can access /api/admin/me
        mockMvc.perform(get("/api/admin/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(USER_EMAIL));
    }

    @Test
    void replayingTotpCodeIsRejected() throws Exception {
        User user = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        mfaService.initiateSetup(user);
        String secret = encryptionService.decrypt(user.getMfaSecret());
        String initialCode = totpService.generateCode(secret, Instant.now().getEpochSecond());
        mfaService.confirmSetup(user, initialCode);
        user.setLastMfaTimeStep(null);
        userRepository.save(user);

        long now = Instant.now().getEpochSecond();
        String totpCode = totpService.generateCode(secret, now);

        // Session 1 uses the code
        MvcResult loginResult1 = mockMvc.perform(post("/login").param("username", USER_EMAIL).param("password", USER_PASSWORD).with(csrf())).andReturn();
        Cookie cookie1 = loginResult1.getResponse().getCookie("SESSION");
        mockMvc.perform(post("/mfa-challenge").cookie(cookie1).param("code", totpCode).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Session 2 attempts to replay the SAME code in the same window
        MvcResult loginResult2 = mockMvc.perform(post("/login").param("username", USER_EMAIL).param("password", USER_PASSWORD).with(csrf())).andReturn();
        Cookie cookie2 = loginResult2.getResponse().getCookie("SESSION");
        mockMvc.perform(post("/mfa-challenge").cookie(cookie2).param("code", totpCode).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mfa-challenge?error"));
    }

    @Test
    void recoveryCodeCompletesAuthenticationExactlyOnce() throws Exception {
        User user = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        mfaService.initiateSetup(user);
        String secret = encryptionService.decrypt(user.getMfaSecret());
        String initialCode = totpService.generateCode(secret, Instant.now().getEpochSecond());
        List<String> recoveryCodes = mfaService.confirmSetup(user, initialCode);

        String firstRecoveryCode = recoveryCodes.get(0);

        // First login with recovery code succeeds
        MvcResult loginResult1 = mockMvc.perform(post("/login").param("username", USER_EMAIL).param("password", USER_PASSWORD).with(csrf())).andReturn();
        Cookie cookie1 = loginResult1.getResponse().getCookie("SESSION");
        mockMvc.perform(post("/mfa-challenge").cookie(cookie1).param("code", firstRecoveryCode).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Second login with SAME recovery code fails
        MvcResult loginResult2 = mockMvc.perform(post("/login").param("username", USER_EMAIL).param("password", USER_PASSWORD).with(csrf())).andReturn();
        Cookie cookie2 = loginResult2.getResponse().getCookie("SESSION");
        mockMvc.perform(post("/mfa-challenge").cookie(cookie2).param("code", firstRecoveryCode).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mfa-challenge?error"));
    }

    @Test
    void invalidMfaAttemptsIncrementFailedCountAndTriggerLockout() throws Exception {
        User user = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        mfaService.initiateSetup(user);
        String secret = encryptionService.decrypt(user.getMfaSecret());
        String initialCode = totpService.generateCode(secret, Instant.now().getEpochSecond());
        mfaService.confirmSetup(user, initialCode);

        MvcResult loginResult = mockMvc.perform(post("/login").param("username", USER_EMAIL).param("password", USER_PASSWORD).with(csrf())).andReturn();
        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");

        // Submit bad codes repeatedly
        for (int i = 0; i < maxFailedAttempts; i++) {
            mockMvc.perform(post("/mfa-challenge")
                            .cookie(sessionCookie)
                            .param("code", "000000")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/mfa-challenge?error"));
        }

        User lockedUser = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        assertThat(lockedUser.isAccountLocked()).as("Account must be locked after reaching max failed attempts").isTrue();
        assertThat(lockedUser.getFailedLoginAttempts()).isGreaterThanOrEqualTo(maxFailedAttempts);
    }
}
