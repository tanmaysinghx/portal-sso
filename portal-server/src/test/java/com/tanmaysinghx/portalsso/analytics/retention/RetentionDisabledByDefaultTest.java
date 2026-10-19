package com.tanmaysinghx.portalsso.analytics.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.tanmaysinghx.portalsso.analytics.entity.LoginEvent;
import com.tanmaysinghx.portalsso.analytics.repository.LoginEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Pins the default login-event window until an administrator saves a different period. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:retention-default;MODE=PostgreSQL",
        "app.seed.test-data=false"
})
class RetentionDisabledByDefaultTest {

    @Autowired
    private LoginEventRepository repository;

    @Autowired
    private LoginEventRetentionJob job;

    @Autowired
    private RetentionProperties properties;

    @Test
    void loginEventRetentionDefaultsTo48Hours() {
        assertThat(properties.isLoginEventRetentionEnabled()).isTrue();
        assertThat(properties.loginEventsHours()).isEqualTo(48);
    }

    @Test
    void ancientEventsArePurgedBeyond48Hours() {
        repository.save(new LoginEvent(
                "ancient@example.com", true, Instant.now().minus(72, ChronoUnit.HOURS)));
        repository.save(new LoginEvent(
                "recent@example.com", true, Instant.now().minus(12, ChronoUnit.HOURS)));

        assertThat(job.run()).isEqualTo(1);
        assertThat(repository.findAll())
                .extracting(LoginEvent::getEmail)
                .containsExactly("recent@example.com");
    }
}
