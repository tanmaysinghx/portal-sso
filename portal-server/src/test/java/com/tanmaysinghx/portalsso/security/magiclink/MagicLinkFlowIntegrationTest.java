package com.tanmaysinghx.portalsso.security.magiclink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanmaysinghx.portalsso.mail.entity.EmailSettings;
import com.tanmaysinghx.portalsso.mail.entity.SmtpSettings;
import com.tanmaysinghx.portalsso.mail.entity.SmtpTransportSecurity;
import com.tanmaysinghx.portalsso.mail.repository.EmailSettingsRepository;
import com.tanmaysinghx.portalsso.mail.repository.SmtpSettingsRepository;
import com.tanmaysinghx.portalsso.security.magiclink.entity.MagicLinkPurpose;
import com.tanmaysinghx.portalsso.security.magiclink.entity.MagicLinkToken;
import com.tanmaysinghx.portalsso.security.magiclink.repository.MagicLinkTokenRepository;
import com.tanmaysinghx.portalsso.security.magiclink.service.MagicLinkService;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The passwordless flow end to end: issuing, redeeming, and every way a link is supposed to stop
 * working.
 *
 * <p>Tokens are minted directly through the repository rather than by reading them out of a sent
 * email. There is no relay configured in the test profile, and a fake one would be testing the fake
 * — what matters here is what the server accepts, which is exactly what this exercises.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MagicLinkFlowIntegrationTest {

    private static final String EXISTING = "magic-existing@example.com";
    private static final String UNKNOWN = "magic-unknown@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MagicLinkTokenRepository tokenRepository;

    @Autowired
    private EmailSettingsRepository emailSettingsRepository;

    @Autowired
    private SmtpSettingsRepository smtpSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MagicLinkService magicLinkService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void reset() {
        tokenRepository.deleteAll();
        userRepository.findByEmail(EXISTING).ifPresent(userRepository::delete);
        userRepository.findByEmail(UNKNOWN).ifPresent(userRepository::delete);
        emailSettingsRepository.deleteAll();
        smtpSettingsRepository.deleteAll();
    }


    /**
     * Leaves no configuration behind. These rows are server-wide and the Spring context is shared
     * across test classes, so a left-behind row with magic links enabled changes what unrelated
     * tests see — it already broke AuthPageControllerTest by rendering a second form on the
     * sign-in page.
     */
    @AfterEach
    void clearServerWideSettings() {
        emailSettingsRepository.deleteAll();
        smtpSettingsRepository.deleteAll();
        tokenRepository.deleteAll();
    }

    /** Upserts, so a test can switch the feature off mid-flight the way an administrator would. */
    private void configure(boolean magicLinkEnabled, boolean registrationEnabled) {
        EmailSettings settings = emailSettingsRepository
                .findByConfigKey(EmailSettings.SINGLETON_KEY)
                .orElseGet(EmailSettings::new);
        settings.setMagicLinkEnabled(magicLinkEnabled);
        settings.setMagicLinkRegistrationEnabled(registrationEnabled);
        emailSettingsRepository.save(settings);
    }

    /**
     * Configures a relay so requests get past the deliverability pre-flight. Nothing listens on
     * port 1, so the send itself fails — which is the point for the enumeration test: the response
     * must be identical whether or not an account exists, even when delivery is broken.
     */
    private void configureUnreachableRelay() {
        SmtpSettings smtp = smtpSettingsRepository
                .findByConfigKey(SmtpSettings.SINGLETON_KEY)
                .orElseGet(SmtpSettings::new);
        smtp.setEnabled(true);
        smtp.setHost("127.0.0.1");
        smtp.setPort(1);
        smtp.setTransportSecurity(SmtpTransportSecurity.NONE);
        smtp.setFromAddress("portal@example.com");
        smtpSettingsRepository.save(smtp);
    }

    private User createEnabledUser() {
        Role role = roleRepository.findByName("ROLE_USER").orElseThrow();
        User user = new User(EXISTING, passwordEncoder.encode("NotUsedHere123!"));
        user.setEnabled(true);
        user.addRole(role);
        return userRepository.save(user);
    }

    /**
     * Mints a token the way the service does, returning the raw value. Reflection reaches the
     * private hash so the test cannot accidentally encode a second, divergent definition of it —
     * if the hashing changes, this follows rather than silently testing the old scheme.
     */
    private String issueToken(String email, MagicLinkPurpose purpose, Instant expiresAt) throws Exception {
        String raw = "test-token-" + java.util.UUID.randomUUID();
        Method hash = MagicLinkService.class.getDeclaredMethod("hash", String.class);
        hash.setAccessible(true);
        String hashed = (String) hash.invoke(null, raw);
        tokenRepository.save(new MagicLinkToken(hashed, email, purpose, expiresAt, "127.0.0.1"));
        return raw;
    }

    // ---------------------------------------------------------------- policy

    @Test
    void thePolicyEndpointReportsTheFeatureOff() throws Exception {
        configure(false, false);
        mockMvc.perform(get("/api/public/magic-link/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.registrationEnabled").value(false));
    }

    @Test
    void thePolicyEndpointReportsTheFeatureOn() throws Exception {
        configure(true, true);
        mockMvc.perform(get("/api/public/magic-link/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.registrationEnabled").value(true));
    }

    // --------------------------------------------------------------- request

    @Test
    void requestingALinkWhileTheFeatureIsOffIsRefused() throws Exception {
        configure(false, false);
        // A property of the server, not of the address, so saying so leaks nothing.
        mockMvc.perform(post("/api/public/magic-link/request")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EXISTING))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRTL-4010"));
    }

    @Test
    void requestingALinkWithNoRelayFailsTheSameWayForEveryAddress() throws Exception {
        configure(true, true);
        createEnabledUser();

        // Deliberately checked before the address is looked at. When this ran at send time instead,
        // a known address got 503 from the failed send and an unknown one got 200 — enumeration by
        // status code on any deployment with a misconfigured relay.
        mockMvc.perform(post("/api/public/magic-link/request")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EXISTING))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRTL-5002"));

        mockMvc.perform(post("/api/public/magic-link/request")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", UNKNOWN))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRTL-5002"));
    }

    @Test
    void anUnknownAddressGetsTheSameAnswerAsAKnownOne() throws Exception {
        configure(true, false);
        configureUnreachableRelay();
        createEnabledUser();

        String knownResponse = mockMvc.perform(post("/api/public/magic-link/request")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EXISTING))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String unknownResponse = mockMvc.perform(post("/api/public/magic-link/request")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", UNKNOWN))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Byte-identical. Any difference here — a word, a field — is an account-enumeration oracle
        // on an unauthenticated endpoint.
        assertThat(knownResponse).isEqualTo(unknownResponse);

        // And no token was minted for the address that has no account and cannot self-register.
        assertThat(tokenRepository.findAll())
                .noneMatch(token -> token.getEmail().equals(UNKNOWN));
    }

    @Test
    void aMalformedAddressIsRejected() throws Exception {
        configure(true, true);
        mockMvc.perform(post("/api/public/magic-link/request")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void aDisabledAccountGetsNoLink() {
        configure(true, false);
        configureUnreachableRelay();
        User user = createEnabledUser();
        user.setEnabled(false);
        userRepository.save(user);

        magicLinkService.requestLink(EXISTING, "127.0.0.1", "test-agent");

        // A lockout or a disable exists to stop sign-ins. A channel around it is not a feature.
        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    @Transactional
    void aLockedAccountGetsNoLink() {
        configure(true, false);
        configureUnreachableRelay();
        User user = createEnabledUser();
        user.setAccountLocked(true);
        userRepository.save(user);

        magicLinkService.requestLink(EXISTING, "127.0.0.1", "test-agent");

        assertThat(tokenRepository.findAll()).isEmpty();
    }

    // ---------------------------------------------------------------- redeem

    @Test
    void avalidLinkSignsTheUserIn() throws Exception {
        configure(true, false);
        createEnabledUser();
        String token = issueToken(EXISTING, MagicLinkPurpose.SIGN_IN, Instant.now().plusSeconds(600));

        mockMvc.perform(get("/magic-link").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertThat(tokenRepository.findAll().getFirst().isConsumed()).isTrue();
    }

    @Test
    void aLinkWorksOnlyOnce() throws Exception {
        configure(true, false);
        createEnabledUser();
        String token = issueToken(EXISTING, MagicLinkPurpose.SIGN_IN, Instant.now().plusSeconds(600));

        mockMvc.perform(get("/magic-link").param("token", token))
                .andExpect(status().is3xxRedirection());

        // The link lives on in the mailbox, and in the logs of every scanner it passed through.
        mockMvc.perform(get("/magic-link").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("magic-link-error"));
    }

    @Test
    void anExpiredLinkIsRefused() throws Exception {
        configure(true, false);
        createEnabledUser();
        String token = issueToken(EXISTING, MagicLinkPurpose.SIGN_IN, Instant.now().minusSeconds(60));

        mockMvc.perform(get("/magic-link").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("magic-link-error"));
    }

    @Test
    void anUnknownTokenIsRefused() throws Exception {
        configure(true, false);
        mockMvc.perform(get("/magic-link").param("token", "a-token-nobody-issued"))
                .andExpect(status().isOk())
                .andExpect(view().name("magic-link-error"));
    }

    @Test
    void aMissingTokenIsRefused() throws Exception {
        configure(true, false);
        mockMvc.perform(get("/magic-link"))
                .andExpect(status().isOk())
                .andExpect(view().name("magic-link-error"));
    }

    @Test
    void aLinkIsRefusedOnceTheFeatureIsSwitchedOff() throws Exception {
        configure(true, false);
        createEnabledUser();
        String token = issueToken(EXISTING, MagicLinkPurpose.SIGN_IN, Instant.now().plusSeconds(600));

        // An administrator turning the feature off must invalidate links already in flight,
        // otherwise "off" means "off for new requests only" and the outstanding ones keep working
        // for as long as their TTL allows.
        configure(false, false);

        mockMvc.perform(get("/magic-link").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("magic-link-error"));
    }

    @Test
    @Transactional
    void issuingANewLinkCancelsTheOutstandingOne() throws Exception {
        configure(true, false);
        configureUnreachableRelay();
        createEnabledUser();
        String first = issueToken(EXISTING, MagicLinkPurpose.SIGN_IN, Instant.now().plusSeconds(600));

        // Requesting again supersedes it. Three "send me a link" clicks must not leave three
        // working credentials in one mailbox, where the oldest is the likeliest to have leaked.
        magicLinkService.requestLink(EXISTING, "127.0.0.1", "test-agent");

        assertThat(tokenRepository.findAll())
                .as("the superseded link and its replacement")
                .hasSize(2);
        assertThat(tokenRepository.findAll())
                .filteredOn(token -> token.getConsumedAt() == null)
                .as("exactly one link is still redeemable")
                .hasSize(1);

        // And the one that was cancelled is specifically the earlier one.
        mockMvc.perform(get("/magic-link").param("token", first))
                .andExpect(status().isOk())
                .andExpect(view().name("magic-link-error"));
    }

    // -------------------------------------------------------- sign-up purpose

    @Test
    void aSignUpLinkCreatesTheAccount() throws Exception {
        configure(true, true);
        String token = issueToken(UNKNOWN, MagicLinkPurpose.SIGN_UP, Instant.now().plusSeconds(600));

        mockMvc.perform(get("/magic-link").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        User created = userRepository.findByEmail(UNKNOWN).orElseThrow();
        assertThat(created.isEnabled()).isTrue();
    }

    @Test
    void anAccountCreatedByLinkHasNoUsablePassword() throws Exception {
        configure(true, true);
        String token = issueToken(UNKNOWN, MagicLinkPurpose.SIGN_UP, Instant.now().plusSeconds(600));

        mockMvc.perform(get("/magic-link").param("token", token))
                .andExpect(status().is3xxRedirection());

        User created = userRepository.findByEmail(UNKNOWN).orElseThrow();
        // Hashed, not blank, and not a shared sentinel — the plaintext is a value nobody holds, so
        // password sign-in is impossible for this account rather than possible with a known secret.
        // Argon2 — see PasswordEncoderConfig.
        assertThat(created.getPasswordHash()).isNotBlank().startsWith("$argon2");
        assertThat(passwordEncoder.matches("", created.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches("password", created.getPasswordHash())).isFalse();
    }

    @Test
    void aSignUpLinkIsRefusedOnceRegistrationIsSwitchedOff() throws Exception {
        configure(true, true);
        String token = issueToken(UNKNOWN, MagicLinkPurpose.SIGN_UP, Instant.now().plusSeconds(600));

        configure(true, false);

        mockMvc.perform(get("/magic-link").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("magic-link-error"));
        assertThat(userRepository.findByEmail(UNKNOWN)).isEmpty();
    }

    @Test
    void aSignInLinkForAnAddressWithNoAccountDoesNotCreateOne() throws Exception {
        configure(true, true);
        // Purpose binding: a SIGN_IN token must not fall through to account creation just because
        // registration happens to be enabled.
        String token = issueToken(UNKNOWN, MagicLinkPurpose.SIGN_IN, Instant.now().plusSeconds(600));

        mockMvc.perform(get("/magic-link").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("magic-link-error"));
        assertThat(userRepository.findByEmail(UNKNOWN)).isEmpty();
    }

    // -------------------------------------------------------------------- MFA

    @Test
    void anAccountWithMfaStillGetsTheChallenge() throws Exception {
        configure(true, false);
        User user = createEnabledUser();
        user.setMfaEnabled(true);
        user.setMfaSecret("encrypted-secret-placeholder");
        userRepository.save(user);

        String token = issueToken(EXISTING, MagicLinkPurpose.SIGN_IN, Instant.now().plusSeconds(600));

        // A second factor exists so one compromised channel is not enough, and email is a channel.
        // The link proves the address; it does not stand in for the factor.
        mockMvc.perform(get("/magic-link").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mfa-challenge"));
    }

    // ------------------------------------------------------------- retention

    @Test
    @Transactional
    void thePurgeDeletesOnlyTokensPastTheRetentionTail() throws Exception {
        configure(true, false);
        issueToken(EXISTING, MagicLinkPurpose.SIGN_IN, Instant.now().minusSeconds(60));
        // Far enough out that it survives the +8d clock below, so the assertion distinguishes
        // "deleted what was past the tail" from "deleted everything".
        issueToken(UNKNOWN, MagicLinkPurpose.SIGN_IN, Instant.now().plus(java.time.Duration.ofDays(30)));

        // Default retention is 7 days, so a minute-old expired token is kept — that tail is what
        // answers "was a link issued for this address?" after an incident.
        assertThat(magicLinkService.purgeExpired(Instant.now())).isZero();
        assertThat(tokenRepository.count()).isEqualTo(2);

        // Far enough past the tail and the expired one goes; the live one stays.
        assertThat(magicLinkService.purgeExpired(Instant.now().plus(java.time.Duration.ofDays(8)))).isEqualTo(1);
        assertThat(tokenRepository.count()).isEqualTo(1);
    }
}
