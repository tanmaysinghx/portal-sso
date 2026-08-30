package com.tanmaysinghx.portalsso.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.config.TestDataSeeder;
import com.tanmaysinghx.portalsso.security.mfa.MfaEncryptionService;
import com.tanmaysinghx.portalsso.security.mfa.TotpService;
import com.tanmaysinghx.portalsso.user.entity.RecoveryCode;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RecoveryCodeRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserMfaResetTest {

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

    private static final String ADMIN_EMAIL = TestDataSeeder.TEST_ADMIN_EMAIL;
    private static final String USER_EMAIL = TestDataSeeder.TEST_USER_EMAIL;

    @BeforeEach
    void setUp() {
        User user = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        user.setMfaEnabled(true);
        user.setMfaSecret(encryptionService.encrypt(totpService.generateSecret()));
        user.setLastMfaTimeStep(12345L);
        userRepository.save(user);

        recoveryCodeRepository.deleteByUser(user);
        recoveryCodeRepository.save(new RecoveryCode(user, "test-hash"));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        User user = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setLastMfaTimeStep(null);
        userRepository.save(user);
        recoveryCodeRepository.deleteByUser(user);
    }

    @Test
    void adminCanResetUserMfa() throws Exception {
        User user = userRepository.findByEmail(USER_EMAIL).orElseThrow();

        mockMvc.perform(post("/api/admin/users/" + user.getId() + "/mfa/reset")
                        .with(user(ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaEnabled").value(false));

        User resetUser = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        assertThat(resetUser.isMfaEnabled()).isFalse();
        assertThat(resetUser.getMfaSecret()).isNull();
        assertThat(resetUser.getLastMfaTimeStep()).isNull();
        assertThat(recoveryCodeRepository.findByUser(resetUser)).isEmpty();
    }

    @Test
    void nonAdminCannotResetUserMfa() throws Exception {
        User user = userRepository.findByEmail(USER_EMAIL).orElseThrow();

        mockMvc.perform(post("/api/admin/users/" + user.getId() + "/mfa/reset")
                        .with(user(USER_EMAIL).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        User unchangedUser = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        assertThat(unchangedUser.isMfaEnabled()).isTrue();
    }
}
