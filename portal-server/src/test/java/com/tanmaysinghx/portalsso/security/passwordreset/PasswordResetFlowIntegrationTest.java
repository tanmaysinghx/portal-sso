package com.tanmaysinghx.portalsso.security.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.repository.AuditEventRepository;
import com.tanmaysinghx.portalsso.mail.entity.SmtpSettings;
import com.tanmaysinghx.portalsso.mail.entity.SmtpTransportSecurity;
import com.tanmaysinghx.portalsso.mail.repository.SmtpSettingsRepository;
import com.tanmaysinghx.portalsso.security.passwordreset.entity.PasswordResetToken;
import com.tanmaysinghx.portalsso.security.passwordreset.repository.PasswordResetTokenRepository;
import com.tanmaysinghx.portalsso.security.token.OpaqueTokens;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * "Forgot password" end to end. Like the magic-link tests, tokens for the redeem side are minted
 * through the repository: the test relay deliberately points at a closed port, and what matters is
 * what the server accepts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetFlowIntegrationTest {

    private static final String EMAIL = "reset-existing@example.com";
    private static final String UNKNOWN = "reset-unknown@example.com";
    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "FreshPassword456!";

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private SmtpSettingsRepository smtpSettingsRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    private User user;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        smtpSettingsRepository.deleteAll();
        user = userRepository.findByEmail(EMAIL).orElseGet(() -> new User(EMAIL, passwordEncoder.encode(OLD_PASSWORD)));
        user.setPasswordHash(passwordEncoder.encode(OLD_PASSWORD));
        user.setEnabled(true);
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        user = userRepository.save(user);
    }

    /** Server-wide rows; the Spring context is shared, so none may be left behind. */
    @AfterEach
    void tearDown() {
        smtpSettingsRepository.deleteAll();
        tokenRepository.deleteAll();
    }

    /** Nothing listens on port 1: gets requests past the deliverability check, then fails to send. */
    private void configureUnreachableRelay() {
        SmtpSettings smtp = smtpSettingsRepository.findByConfigKey(SmtpSettings.SINGLETON_KEY).orElseGet(SmtpSettings::new);
        smtp.setEnabled(true);
        smtp.setHost("127.0.0.1");
        smtp.setPort(1);
        smtp.setTransportSecurity(SmtpTransportSecurity.NONE);
        smtp.setFromAddress("portal@example.com");
        smtpSettingsRepository.save(smtp);
    }

    private String issueToken(Instant expiresAt) {
        String raw = "reset-token-" + UUID.randomUUID();
        tokenRepository.save(new PasswordResetToken(OpaqueTokens.hash(raw), user.getId(), EMAIL, expiresAt, "127.0.0.1"));
        return raw;
    }

    private String requestBody(String email) {
        return "{\"email\":\"" + email + "\"}";
    }

    private String confirmBody(String token, String password) {
        return "{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}";
    }

    // ---------------------------------------------------------------- policy and request

    @Test
    void notOfferedWhileNoRelayIsConfigured() throws Exception {
        mockMvc.perform(get("/api/public/password-reset/policy"))
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(post("/api/public/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody(EMAIL)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRTL-5002"));
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void answersIdenticallyForKnownAndUnknownAddresses() throws Exception {
        configureUnreachableRelay();
        mockMvc.perform(get("/api/public/password-reset/policy")).andExpect(jsonPath("$.available").value(true));

        String known = mockMvc.perform(post("/api/public/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody(EMAIL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(post("/api/public/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody(UNKNOWN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(known);
        // Only the real account got a link, even though the send itself failed.
        assertThat(tokenRepository.findAll()).extracting(PasswordResetToken::getEmail).containsExactly(EMAIL);
        assertThat(auditEventRepository.findAll())
                .anyMatch(e -> e.getAction() == AuditAction.PASSWORD_RESET_REQUESTED && EMAIL.equals(e.getTargetLabel()));
    }

    @Test
    void issuingANewLinkCancelsTheOldOneAndThePerAddressCapHolds() throws Exception {
        configureUnreachableRelay();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/public/password-reset/request").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(requestBody(EMAIL)))
                    .andExpect(status().isOk());
        }
        // Capped at three per window, and only the newest is still usable.
        assertThat(tokenRepository.findAll()).hasSize(3);
        assertThat(tokenRepository.findAll().stream().filter(t -> !t.isConsumed())).hasSize(1);
    }

    @Test
    void disabledAccountsAreSilentlyIgnored() throws Exception {
        configureUnreachableRelay();
        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/public/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody(EMAIL)))
                .andExpect(status().isOk());
        assertThat(tokenRepository.count()).isZero();
    }

    // ---------------------------------------------------------------- inspect and confirm

    @Test
    void inspectNamesTheAccountWithoutSpendingTheLink() throws Exception {
        String token = issueToken(Instant.now().plus(30, ChronoUnit.MINUTES));
        mockMvc.perform(post("/api/public/password-reset/inspect").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.passwordPolicy.minLength").isNumber());
        assertThat(tokenRepository.findAll()).noneMatch(PasswordResetToken::isConsumed);
    }

    @Test
    void rejectsUnknownExpiredAndUsedLinksDistinctly() throws Exception {
        mockMvc.perform(post("/api/public/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody("not-a-real-token", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-4014"));

        String expired = issueToken(Instant.now().minusSeconds(1));
        mockMvc.perform(post("/api/public/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(expired, NEW_PASSWORD)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PRTL-4016"));

        String valid = issueToken(Instant.now().plus(30, ChronoUnit.MINUTES));
        mockMvc.perform(post("/api/public/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(valid, NEW_PASSWORD)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/public/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(valid, "AnotherPassword789!")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PRTL-4017"));
    }

    @Test
    void aRejectedPasswordLeavesTheLinkUsable() throws Exception {
        String token = issueToken(Instant.now().plus(30, ChronoUnit.MINUTES));
        mockMvc.perform(post("/api/public/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(token, "weak")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-2011"));
        mockMvc.perform(post("/api/public/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(token, OLD_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-2013"));

        assertThat(tokenRepository.findAll()).noneMatch(PasswordResetToken::isConsumed);
    }

    @Test
    void aSuccessfulResetSetsThePasswordClearsLockoutAndSignsOutEverywhere() throws Exception {
        user.setAccountLocked(true);
        user.setFailedLoginAttempts(5);
        userRepository.save(user);
        String sessionId = createSessionFor(sessionRepository, EMAIL);
        String token = issueToken(Instant.now().plus(30, ChronoUnit.MINUTES));
        String other = issueToken(Instant.now().plus(30, ChronoUnit.MINUTES));

        mockMvc.perform(post("/api/public/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(token, NEW_PASSWORD)))
                .andExpect(status().isOk());

        User after = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, after.getPasswordHash())).isTrue();
        assertThat(after.isAccountLocked()).isFalse();
        assertThat(after.getFailedLoginAttempts()).isZero();
        assertThat(sessionRepository.findById(sessionId)).isNull();
        // Every outstanding link for the account is spent, not just the one used.
        assertThat(tokenRepository.findByTokenHash(OpaqueTokens.hash(other)).orElseThrow().isConsumed()).isTrue();
        assertThat(auditEventRepository.findAll())
                .anyMatch(e -> e.getAction() == AuditAction.PASSWORD_RESET_COMPLETED && EMAIL.equals(e.getTargetLabel()));

        // A reset sets the password and stops: the new password then works at the ordinary sign-in.
        mockMvc.perform(post("/login").param("username", EMAIL).param("password", NEW_PASSWORD).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("error"));
    }

    @Test
    void aDisabledAccountCannotBeResetEvenWithAValidLink() throws Exception {
        String token = issueToken(Instant.now().plus(30, ChronoUnit.MINUTES));
        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/public/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(token, NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRTL-1005"));
    }

    private static <S extends Session> String createSessionFor(FindByIndexNameSessionRepository<S> repository, String email) {
        S session = repository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, email);
        repository.save(session);
        return session.getId();
    }
}
