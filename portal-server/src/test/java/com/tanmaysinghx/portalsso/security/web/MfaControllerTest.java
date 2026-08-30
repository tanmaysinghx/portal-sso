package com.tanmaysinghx.portalsso.security.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanmaysinghx.portalsso.config.TestDataSeeder;
import com.tanmaysinghx.portalsso.security.mfa.MfaEncryptionService;
import com.tanmaysinghx.portalsso.security.mfa.TotpService;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RecoveryCodeRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MfaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private TotpService totpService;

    @Autowired
    private MfaEncryptionService encryptionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EMAIL = TestDataSeeder.TEST_USER_EMAIL;

    @BeforeEach
    @org.junit.jupiter.api.AfterEach
    void resetState() {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setLastMfaTimeStep(null);
        userRepository.save(user);
        recoveryCodeRepository.deleteByUser(user);
    }

    @Test
    void unauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/mfa/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMfaStatusReturnsCurrentState() throws Exception {
        mockMvc.perform(get("/api/mfa/status").with(user(EMAIL)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaEnabled").value(false));
    }

    @Test
    void setupGeneratesSecretWithoutEnablingMfa() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mfa/setup").with(user(EMAIL)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.provisioningUri").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String secret = json.get("secret").asText();

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.isMfaEnabled()).as("MFA must not be enabled until confirmed").isFalse();
        assertThat(user.getMfaSecret()).isNotNull();
        assertThat(encryptionService.decrypt(user.getMfaSecret())).isEqualTo(secret);
    }

    @Test
    void confirmFailsWithInvalidCode() throws Exception {
        // Initiate setup first
        mockMvc.perform(post("/api/mfa/setup").with(user(EMAIL)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mfa/confirm")
                        .with(user(EMAIL))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.isMfaEnabled()).as("MFA must remain disabled after invalid confirmation").isFalse();
    }

    @Test
    void confirmEnablesMfaAndIssuesRecoveryCodes() throws Exception {
        MvcResult setupResult = mockMvc.perform(post("/api/mfa/setup").with(user(EMAIL)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode setupJson = objectMapper.readTree(setupResult.getResponse().getContentAsString());
        String secret = setupJson.get("secret").asText();

        String validCode = totpService.generateCode(secret, Instant.now().getEpochSecond());

        MvcResult confirmResult = mockMvc.perform(post("/api/mfa/confirm")
                        .with(user(EMAIL))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + validCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaEnabled").value(true))
                .andExpect(jsonPath("$.recoveryCodes").isArray())
                .andReturn();

        JsonNode confirmJson = objectMapper.readTree(confirmResult.getResponse().getContentAsString());
        assertThat(confirmJson.get("recoveryCodes")).hasSize(8);

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.isMfaEnabled()).isTrue();
        assertThat(recoveryCodeRepository.findByUser(user)).hasSize(8);
    }

    @Test
    void disableMfaRequiresCorrectPassword() throws Exception {
        // First enable MFA
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setMfaEnabled(true);
        user.setMfaSecret(encryptionService.encrypt(totpService.generateSecret()));
        userRepository.save(user);

        // Wrong password fails
        mockMvc.perform(post("/api/mfa/disable")
                        .with(user(EMAIL))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"WrongPassword!\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().isMfaEnabled()).isTrue();

        // Correct password succeeds
        mockMvc.perform(post("/api/mfa/disable")
                        .with(user(EMAIL))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + TestDataSeeder.TEST_USER_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaEnabled").value(false));

        User disabledUser = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(disabledUser.isMfaEnabled()).isFalse();
        assertThat(disabledUser.getMfaSecret()).isNull();
    }
}
