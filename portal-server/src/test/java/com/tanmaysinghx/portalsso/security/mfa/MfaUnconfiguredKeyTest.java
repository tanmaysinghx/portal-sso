package com.tanmaysinghx.portalsso.security.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A server with no encryption key configured and nothing enrolled.
 *
 * <p>This is the state where refusing to boot would be wrong — nothing is at risk, and a fresh
 * deployment should not be blocked over a feature nobody is using yet. It starts, warns, and makes
 * the unsafe state unreachable by refusing enrolment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mfanokey;MODE=PostgreSQL",
        "app.seed.test-data=false",
        "app.security.mfa.encryption-key="
})
class MfaUnconfiguredKeyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MfaEncryptionService encryptionService;

    @Autowired
    private MfaKeyStartupCheck startupCheck;

    @Test
    void theServerStartsWhenNoKeyIsSetAndNobodyHasEnrolled() {
        // Reaching this test at all proves startup succeeded rather than refusing.
        assertThat(encryptionService.isConfigured()).isFalse();
    }

    /**
     * Enrolment is refused rather than performed with an unprotected secret. Storing one anyway
     * would be worse than having no MFA, because the user would believe they had a second factor.
     */
    @Test
    void enrolmentIsRefusedRatherThanStoringAnUnprotectedSecret() throws Exception {
        User u = new User("wants-mfa@example.com", "irrelevant");
        userRepository.save(u);

        mockMvc.perform(post("/api/mfa/setup").with(user("wants-mfa@example.com")).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRTL-1009"));

        assertThat(userRepository.findByEmail("wants-mfa@example.com").orElseThrow().getMfaSecret())
                .as("no secret may be written without a key to protect it")
                .isNull();
    }

    /**
     * The unsafe legacy state, and the only one where refusing is unambiguously right: real secrets
     * exist and the key protecting them is whatever was published in the source. Starting would mean
     * running with second factors anyone can decrypt from a database dump.
     */
    @Test
    @org.springframework.transaction.annotation.Transactional
    void aServerHoldingSecretsWithNoKeyRefusesToStart() {
        User enrolled = new User("already-enrolled@example.com", "irrelevant");
        enrolled.setMfaEnabled(true);
        // Written by an older build, under the key that used to be the default.
        enrolled.setMfaSecret(new MfaEncryptionService(
                new MfaEncryptionProperties(MfaEncryptionService.RETIRED_DEFAULT_KEY, null))
                .encrypt("JBSWY3DPEHPK3PXP"));
        userRepository.save(enrolled);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> startupCheck.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption-key is not set")
                // The message has to say how to fix it, or refusing is just an outage.
                .hasMessageContaining("re-encrypted under it automatically");
    }
}
