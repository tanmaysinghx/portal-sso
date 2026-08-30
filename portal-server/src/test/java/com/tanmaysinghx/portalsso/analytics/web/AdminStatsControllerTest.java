package com.tanmaysinghx.portalsso.analytics.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.analytics.repository.LoginEventRepository;
import com.tanmaysinghx.portalsso.config.TestDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoginEventRepository loginEventRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void everyRangeReturnsAConsistentlyShapedPayload() throws Exception {
        for (String range : new String[] {"day", "week", "month", "year", "5y", "all"}) {
            mockMvc.perform(get("/api/admin/stats").param("range", range))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totals.totalUsers").isNumber())
                    .andExpect(jsonPath("$.signups").isArray())
                    .andExpect(jsonPath("$.logins").isArray())
                    // Five fixed recency buckets, always present so the chart never changes shape.
                    .andExpect(jsonPath("$.lastLoginBuckets.length()").value(5))
                    .andExpect(jsonPath("$.recentLogins").isArray());
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anUnknownRangeFallsBackRatherThanFailing() throws Exception {
        mockMvc.perform(get("/api/admin/stats").param("range", "not-a-range"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("MONTH"));
    }

    /** Gaps must stay visible: a quiet day is a zero, not a missing point the chart closes over. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void timeSeriesAreZeroFilledAcrossTheWindow() throws Exception {
        mockMvc.perform(get("/api/admin/stats").param("range", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("DAY"))
                .andExpect(jsonPath("$.signups.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(7)))
                .andExpect(jsonPath("$.logins.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(7)));
    }

    @Test
    void aSignInIsRecordedAsALoginEvent() throws Exception {
        long before = loginEventRepository.count();

        mockMvc.perform(post("/login")
                .param("username", TestDataSeeder.TEST_ADMIN_EMAIL)
                .param("password", TestDataSeeder.TEST_ADMIN_PASSWORD)
                .with(csrf()));

        assertThat(loginEventRepository.count())
                .as("the sign-in should have produced a login event")
                .isGreaterThan(before);
    }

    @Test
    void aFailedSignInIsAlsoRecorded() throws Exception {
        long before = loginEventRepository.count();

        mockMvc.perform(post("/login")
                .param("username", TestDataSeeder.TEST_ADMIN_EMAIL)
                .param("password", "wrong-password")
                .with(csrf()));

        assertThat(loginEventRepository.count())
                .as("failed attempts are exactly what the dashboard needs to surface")
                .isGreaterThan(before);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportReturnsCsvAsAnAttachment() throws Exception {
        mockMvc.perform(get("/api/admin/stats/export").param("range", "month"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".csv")))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("occurred_at,email,successful")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminsCannotReadOrExportStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/stats/export")).andExpect(status().isForbidden());
    }
}
