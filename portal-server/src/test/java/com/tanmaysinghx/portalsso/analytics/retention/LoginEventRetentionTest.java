package com.tanmaysinghx.portalsso.analytics.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.tanmaysinghx.portalsso.analytics.entity.LoginEvent;
import com.tanmaysinghx.portalsso.analytics.repository.LoginEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Retention with a 30-day window and a deliberately tiny batch size, so the batching loop actually
 * runs more than once rather than being exercised only in its single-pass form.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:retention;MODE=PostgreSQL",
        "app.seed.test-data=false",
        "app.analytics.retention.login-events-days=30",
        "app.analytics.retention.batch-size=3"
})
class LoginEventRetentionTest {

    @Autowired
    private LoginEventRepository repository;

    @Autowired
    private LoginEventRetentionJob job;

    @BeforeEach
    void reset() {
        repository.deleteAll();
    }

    private void event(String email, long daysAgo) {
        repository.save(new LoginEvent(email, true, Instant.now().minus(daysAgo, ChronoUnit.DAYS)));
    }

    @Test
    void eventsOlderThanTheWindowAreDeletedAndNewerOnesKept() {
        for (int i = 0; i < 7; i++) {
            event("old-" + i + "@example.com", 60);
        }
        for (int i = 0; i < 4; i++) {
            event("recent-" + i + "@example.com", 5);
        }

        int deleted = job.run();

        assertThat(deleted).isEqualTo(7);
        assertThat(repository.findAll())
                .as("only events inside the window survive")
                .hasSize(4)
                .allSatisfy(e -> assertThat(e.getEmail()).startsWith("recent-"));
    }

    /**
     * Seven rows at a batch size of three means three passes. Worth pinning: a loop that stopped
     * after the first batch would look correct on any small dataset and leave a real backlog behind.
     */
    @Test
    void theBatchLoopContinuesUntilTheBacklogIsCleared() {
        for (int i = 0; i < 7; i++) {
            event("old-" + i + "@example.com", 90);
        }

        assertThat(job.run()).isEqualTo(7);
        assertThat(repository.count()).isZero();
    }

    /** An event exactly inside the window must not be deleted by an off-by-one on the cutoff. */
    @Test
    void anEventJustInsideTheWindowSurvives() {
        event("just-inside@example.com", 29);
        event("just-outside@example.com", 31);

        job.run();

        assertThat(repository.findAll())
                .extracting(LoginEvent::getEmail)
                .containsExactly("just-inside@example.com");
    }

    @Test
    void aRunWithNothingToDeleteRemovesNothing() {
        event("recent@example.com", 1);

        assertThat(job.run()).isZero();
        assertThat(repository.count()).isEqualTo(1);
    }
}
