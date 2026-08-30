package com.tanmaysinghx.portalsso.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.tanmaysinghx.portalsso.config.TestDataSeeder;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers {@link LoginAttemptListener}. These columns sat unwritten in the schema for a long time,
 * so the point of these tests is to prove something actually reaches the database now.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginAttemptTrackingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.security.max-failed-login-attempts:5}")
    private int maxFailedAttempts;

    private static final String EMAIL = TestDataSeeder.TEST_USER_EMAIL;

    @BeforeEach
    void resetUserState() {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(null);
        userRepository.save(user);
    }

    private User reload() {
        return userRepository.findByEmail(EMAIL).orElseThrow();
    }

    private void attemptLogin(String password) throws Exception {
        mockMvc.perform(post("/login").param("username", EMAIL).param("password", password).with(csrf()));
    }

    @Test
    void successfulLoginStampsLastLoginAt() throws Exception {
        assertThat(reload().getLastLoginAt()).as("precondition: never logged in").isNull();

        attemptLogin(TestDataSeeder.TEST_USER_PASSWORD);

        assertThat(reload().getLastLoginAt())
                .as("last_login_at should be written on a successful sign-in")
                .isNotNull();
    }

    @Test
    void failedLoginIncrementsCounterWithoutLockingBelowThreshold() throws Exception {
        attemptLogin("wrong-password");

        User user = reload();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.isAccountLocked()).isFalse();
    }

    @Test
    void accountLocksOnceThresholdIsReached() throws Exception {
        for (int i = 0; i < maxFailedAttempts; i++) {
            attemptLogin("wrong-password");
        }

        User user = reload();
        assertThat(user.getFailedLoginAttempts()).isGreaterThanOrEqualTo(maxFailedAttempts);
        assertThat(user.isAccountLocked()).as("account should lock at the configured threshold").isTrue();
    }

    @Test
    void lockedAccountIsRejectedEvenWithTheCorrectPassword() throws Exception {
        for (int i = 0; i < maxFailedAttempts; i++) {
            attemptLogin("wrong-password");
        }
        assertThat(reload().isAccountLocked()).isTrue();

        // The whole point of the flag: the right password must not get you in while locked.
        attemptLogin(TestDataSeeder.TEST_USER_PASSWORD);

        assertThat(reload().getLastLoginAt())
                .as("a locked account must not record a successful sign-in")
                .isNull();
    }

    @Test
    void successfulLoginClearsAnEarlierFailureStreak() throws Exception {
        attemptLogin("wrong-password");
        attemptLogin("wrong-password");
        assertThat(reload().getFailedLoginAttempts()).isEqualTo(2);

        attemptLogin(TestDataSeeder.TEST_USER_PASSWORD);

        assertThat(reload().getFailedLoginAttempts())
                .as("a good sign-in should reset the streak so typos never accumulate into a lockout")
                .isZero();
    }
}
