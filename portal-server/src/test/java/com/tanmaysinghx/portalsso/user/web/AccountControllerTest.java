package com.tanmaysinghx.portalsso.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.entity.AuditEvent;
import com.tanmaysinghx.portalsso.audit.repository.AuditEventRepository;
import com.tanmaysinghx.portalsso.config.TestDataSeeder;
import com.tanmaysinghx.portalsso.security.device.DeviceSignature;
import com.tanmaysinghx.portalsso.security.device.KnownDeviceService;
import com.tanmaysinghx.portalsso.security.device.entity.KnownDevice;
import com.tanmaysinghx.portalsso.security.device.repository.KnownDeviceRepository;
import com.tanmaysinghx.portalsso.security.mfa.MfaEncryptionService;
import com.tanmaysinghx.portalsso.security.mfa.TotpService;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RecoveryCodeRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerTest {

    // A user of its own, so changing its password cannot break tests that sign in as the seeded one.
    private static final String EMAIL = "account-self-service@portalsso.local";
    private static final String PASSWORD = "AccountPassword123!";
    private static final String NEW_PASSWORD = "BrandNewPassword456!";
    private static final String CHROME_MAC =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RecoveryCodeRepository recoveryCodeRepository;
    @Autowired private KnownDeviceRepository knownDeviceRepository;
    @Autowired private KnownDeviceService knownDeviceService;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TotpService totpService;
    @Autowired private MfaEncryptionService encryptionService;
    @Autowired private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetUser() {
        User user = userRepository.findByEmail(EMAIL).orElseGet(() -> new User(EMAIL, passwordEncoder.encode(PASSWORD)));
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFirstName(null);
        user.setLastName(null);
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setLastMfaTimeStep(null);
        user = userRepository.save(user);
        recoveryCodeRepository.deleteByUser(user);
        knownDeviceService.forgetDevicesFor(user.getId());
        sessionRepository.findByPrincipalName(EMAIL).keySet().forEach(sessionRepository::deleteById);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/user/account")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOwnAccountWithPolicyAndMfaState() throws Exception {
        mockMvc.perform(get("/api/user/account").with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.mfaEnabled").value(false))
                .andExpect(jsonPath("$.mfaAvailable").value(true))
                .andExpect(jsonPath("$.recoveryCodesRemaining").value(0))
                .andExpect(jsonPath("$.passwordPolicy.minLength").isNumber());
    }

    @Test
    void updatesProfileAndAuditsFieldNamesOnly() throws Exception {
        mockMvc.perform(patch("/api/user/account/profile")
                        .with(user(EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"  Ada \",\"lastName\":\"Lovelace\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"));

        AuditEvent event = latestAudit(AuditAction.USER_PROFILE_UPDATED);
        assertThat(event.getDetails()).isEqualTo("changed=firstName,lastName");
        assertThat(event.getDetails()).doesNotContain("Ada", "Lovelace");
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordWithoutSigningOut() throws Exception {
        mockMvc.perform(post("/api/user/account/password")
                        .with(user(EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody("NotMyPassword1!", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-2012"));

        assertThat(passwordEncoder.matches(PASSWORD, userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash()))
                .isTrue();
    }

    @Test
    void changePasswordEnforcesPolicyAndRejectsReuse() throws Exception {
        mockMvc.perform(post("/api/user/account/password")
                        .with(user(EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD, "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-2011"));

        mockMvc.perform(post("/api/user/account/password")
                        .with(user(EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-2013"));
    }

    @Test
    void changePasswordUpdatesHashSignsOutOtherSessionsAndAudits() throws Exception {
        String otherSessionId = createSessionFor(sessionRepository, EMAIL);
        assertThat(sessionRepository.findByPrincipalName(EMAIL)).containsKey(otherSessionId);

        mockMvc.perform(post("/api/user/account/password")
                        .with(user(EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otherSessionsSignedOut").value(1));

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(sessionRepository.findById(otherSessionId)).isNull();

        AuditEvent event = latestAudit(AuditAction.USER_PASSWORD_CHANGED);
        assertThat(event.getDetails()).doesNotContain(PASSWORD, NEW_PASSWORD);
    }

    @Test
    void regeneratesRecoveryCodesOnlyWithValidAuthenticatorCode() throws Exception {
        String secret = enableMfa();

        mockMvc.perform(post("/api/user/account/recovery-codes")
                        .with(user(EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-1007"));

        String code = totpService.generateCode(secret, Instant.now().getEpochSecond());
        MvcResult result = mockMvc.perform(post("/api/user/account/recovery-codes")
                        .with(user(EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode codes = objectMapper.readTree(result.getResponse().getContentAsString()).get("recoveryCodes");
        assertThat(codes).hasSize(8);
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(recoveryCodeRepository.findByUserAndUsedAtIsNull(user)).hasSize(8);
        assertThat(latestAudit(AuditAction.USER_RECOVERY_CODES_REGENERATED)).isNotNull();

        mockMvc.perform(get("/api/user/account").with(user(EMAIL)))
                .andExpect(jsonPath("$.mfaEnabled").value(true))
                .andExpect(jsonPath("$.recoveryCodesRemaining").value(8));
    }

    @Test
    void listsDevicesMarkingTheCurrentOneAndForgetsThem() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        DeviceSignature chrome = DeviceSignature.parse(CHROME_MAC);
        KnownDevice device = knownDeviceRepository.save(
                new KnownDevice(user.getId(), chrome.hash(), chrome.label(), Instant.now()));

        mockMvc.perform(get("/api/user/account/devices").with(user(EMAIL)).header("User-Agent", CHROME_MAC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value(chrome.label()))
                .andExpect(jsonPath("$[0].current").value(true));

        mockMvc.perform(delete("/api/user/account/devices/" + device.getId()).with(user(EMAIL)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(knownDeviceRepository.findByUserIdOrderByLastSeenAtDesc(user.getId())).isEmpty();
        assertThat(latestAudit(AuditAction.USER_DEVICE_FORGOTTEN)).isNotNull();
    }

    @Test
    void cannotForgetAnotherUsersDevice() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        DeviceSignature chrome = DeviceSignature.parse(CHROME_MAC);
        KnownDevice device = knownDeviceRepository.save(
                new KnownDevice(user.getId(), chrome.hash(), chrome.label(), Instant.now()));

        mockMvc.perform(delete("/api/user/account/devices/" + device.getId())
                        .with(user(TestDataSeeder.TEST_USER_EMAIL)).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(knownDeviceRepository.findById(device.getId())).isPresent();
    }

    /** Generic so the repository's concrete (package-private) session type never has to be named. */
    private static <S extends Session> String createSessionFor(FindByIndexNameSessionRepository<S> repository, String email) {
        S session = repository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, email);
        repository.save(session);
        return session.getId();
    }

    private String enableMfa() {
        String secret = totpService.generateSecret();
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setMfaEnabled(true);
        user.setMfaSecret(encryptionService.encrypt(secret));
        userRepository.save(user);
        return secret;
    }

    private AuditEvent latestAudit(AuditAction action) {
        List<AuditEvent> events = auditEventRepository.findAll().stream()
                .filter(e -> e.getAction() == action && EMAIL.equals(e.getTargetLabel()))
                .toList();
        assertThat(events).as("audit events for %s", action).isNotEmpty();
        return events.get(events.size() - 1);
    }

    private static String passwordBody(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }
}
