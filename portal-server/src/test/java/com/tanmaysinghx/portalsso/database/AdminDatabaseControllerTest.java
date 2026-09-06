package com.tanmaysinghx.portalsso.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanmaysinghx.portalsso.database.dto.MigrateDatabaseRequest;
import com.tanmaysinghx.portalsso.database.dto.TestConnectionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDatabaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void unauthenticatedAccessIsRefused() throws Exception {
        mockMvc.perform(get("/api/admin/database/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminAccessIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/database/status")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanGetDatabaseStatus() throws Exception {
        mockMvc.perform(get("/api/admin/database/status")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseType").value("H2"))
                .andExpect(jsonPath("$.isEmbedded").value(true));
    }

    @Test
    void adminCanTestDatabaseConnection() throws Exception {
        TestConnectionRequest request = new TestConnectionRequest(
                "H2", null, null, null, "sa", "", "jdbc:h2:mem:testconn;MODE=PostgreSQL");

        mockMvc.perform(post("/api/admin/database/test-connection")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.databaseProduct").value("H2"));
    }

    @Test
    void adminCanMigrateDatabaseToTarget() throws Exception {
        MigrateDatabaseRequest request = new MigrateDatabaseRequest(
                "H2", null, null, null, "sa", "", "jdbc:h2:mem:testmig;MODE=PostgreSQL", false);

        mockMvc.perform(post("/api/admin/database/migrate")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.totalRowsMigrated").isNumber())
                .andExpect(jsonPath("$.tablesMigrated.roles").isNumber());
    }
}
