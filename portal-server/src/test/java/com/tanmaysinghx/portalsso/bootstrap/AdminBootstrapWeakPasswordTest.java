package com.tanmaysinghx.portalsso.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * A short password is refused outright rather than accepted with a warning. A warning scrolls past
 * in a startup log, and what would be left behind is the most privileged account on the server
 * sitting behind a weak password — the bar here is deliberately higher than the 8 characters the
 * ordinary user endpoints accept, because this one is chosen once, by an operator, in a file.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:bootstrap-weak;MODE=PostgreSQL",
        "app.seed.test-data=false",
        "app.bootstrap.admin-email=ops@example.com",
        "app.bootstrap.admin-password=short123"
})
class AdminBootstrapWeakPasswordTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void aPasswordBelowTheMinimumCreatesNoAccountAtAll() {
        assertThat(userRepository.findByEmail("ops@example.com")).isEmpty();
        assertThat(userRepository.count()).isZero();
    }
}
