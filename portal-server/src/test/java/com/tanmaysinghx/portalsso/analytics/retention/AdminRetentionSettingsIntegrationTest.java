package com.tanmaysinghx.portalsso.analytics.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.entity.AuditEvent;
import com.tanmaysinghx.portalsso.audit.repository.AuditEventRepository;
import com.tanmaysinghx.portalsso.analytics.retention.repository.AnalyticsRetentionSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:retention-settings;MODE=PostgreSQL",
        "app.seed.test-data=false"
})
class AdminRetentionSettingsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalyticsRetentionSettingsRepository repository;

    @Autowired
    private AuditEventRepository auditRepository;

    @Autowired
    private AuditEventRetentionJob auditRetentionJob;

    @BeforeEach
    void resetSettings() {
        repository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanReadAndPersistRetentionWindows() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginEventsHours").value(48))
                .andExpect(jsonPath("$.auditEventsHours").value(0));

        mockMvc.perform(put("/api/admin/analytics/retention")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"loginEventsHours\":336,\"auditEventsHours\":2160}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginEventsHours").value(336))
                .andExpect(jsonPath("$.auditEventsHours").value(2160));

        mockMvc.perform(get("/api/admin/analytics/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginEventsHours").value(336))
                .andExpect(jsonPath("$.auditEventsHours").value(2160));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void savedAuditRetentionWindowControlsScheduledPurge() throws Exception {
        mockMvc.perform(put("/api/admin/analytics/retention")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"loginEventsHours\":48,\"auditEventsHours\":24}"))
                .andExpect(status().isOk());

        auditRepository.save(new AuditEvent(
                null, "actor@example.com", AuditAction.USER_LOGIN_SUCCESS, null, null, null, null, null,
                Instant.now().minus(3, ChronoUnit.DAYS)));
        auditRepository.save(new AuditEvent(
                null, "actor@example.com", AuditAction.USER_LOGIN_SUCCESS, null, null, null, null, null,
                Instant.now().minus(1, ChronoUnit.HOURS)));

        assertThat(auditRetentionJob.run()).isEqualTo(1);
        assertThat(auditRepository.findAll().stream()
                        .filter(event -> event.getAction() == AuditAction.USER_LOGIN_SUCCESS))
                .hasSize(1);
    }
}
