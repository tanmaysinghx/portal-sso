package com.tanmaysinghx.portalsso.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Turns the limiter on with a deliberately tiny allowance. The rest of the suite runs with it off
 * (see {@code application-test.yml}) because every MockMvc request shares one source address.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "app.rate-limit.enabled=true",
            // Three back-to-back, refilling slowly enough that the fourth in a burst is refused.
            "app.rate-limit.rules.[/login].capacity=3",
            "app.rate-limit.rules.[/login].per-minute=3",
        })
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiter rateLimiter;

    private int postLogin() throws Exception {
        return mockMvc.perform(post("/login")
                        .param("username", "nobody@example.com")
                        .param("password", "wrong")
                        .with(csrf()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    void requestsBeyondTheBurstAllowanceAreRefusedWith429() throws Exception {
        rateLimiter.reset();

        for (int i = 1; i <= 3; i++) {
            assertThat(postLogin()).as("request %d is within the allowance", i).isNotEqualTo(429);
        }

        assertThat(postLogin()).as("the fourth request exceeds the burst").isEqualTo(429);
    }

    @Test
    void aRefusalTellsTheCallerWhenToRetry() throws Exception {
        rateLimiter.reset();
        for (int i = 0; i < 3; i++) {
            postLogin();
        }

        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", "nobody@example.com")
                        .param("password", "wrong")
                        .with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(429);
        assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER))
                .as("clients need to know how long to back off")
                .isNotNull();
        assertThat(result.getResponse().getContentAsString())
                .as("errors share one shape across the API")
                .contains("PRTL-4029");
    }

    /**
     * The limiter must not become its own denial-of-service vector: an attacker rotating source
     * addresses would otherwise grow the bucket map without bound.
     */
    @Test
    void trackedKeysStayBoundedUnderManyDistinctClients() throws Exception {
        rateLimiter.reset();
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule(5, 60);

        for (int i = 0; i < 3000; i++) {
            rateLimiter.checkAndConsume("/login", rule, "10.0." + (i / 250) + "." + (i % 250));
        }

        assertThat(rateLimiter.trackedKeys())
                .as("bucket map must stay within the configured ceiling")
                .isLessThanOrEqualTo(50_000);
    }

    @Test
    void unmatchedPathsAreNotLimited() throws Exception {
        rateLimiter.reset();
        // /api/admin/** has no rule, so it must pass through untouched however many times it is hit.
        for (int i = 0; i < 25; i++) {
            int status = mockMvc.perform(post("/api/admin/users").with(csrf()))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            assertThat(status).as("no rule matches this path").isNotEqualTo(429);
        }
    }
}
