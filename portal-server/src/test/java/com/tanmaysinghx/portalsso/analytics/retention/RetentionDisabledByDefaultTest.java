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

/**
 * The default must be to keep everything.
 *
 * <p>An upgrade that silently deleted an operator's authentication history — the record they reach
 * for while investigating an incident — would be a worse defect than the unbounded growth retention
 * exists to fix. This pins the default rather than trusting a comment.
 */
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
    void retentionIsOffUnlessAnOperatorTurnsItOn() {
        assertThat(properties.isLoginEventRetentionEnabled()).isFalse();
        assertThat(properties.loginEventsDays()).isZero();
    }

    @Test
    void avowedlyAncientEventsSurviveWhenRetentionIsDisabled() {
        repository.save(new LoginEvent(
                "ancient@example.com", true, Instant.now().minus(3650, ChronoUnit.DAYS)));

        assertThat(job.run()).isZero();
        assertThat(repository.count()).isEqualTo(1);
    }
}
