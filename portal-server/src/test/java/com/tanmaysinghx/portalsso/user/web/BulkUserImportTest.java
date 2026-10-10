package com.tanmaysinghx.portalsso.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.web.dto.BulkUserImportRequest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulkUserImportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void importUsers_withValidCsv_shouldImportSuccessfully() throws Exception {
        long ts = System.currentTimeMillis();
        String email1 = "csvuser1_" + ts + "@example.com";
        String email2 = "csvuser2_" + ts + "@example.com";

        String csvData = """
                email,firstName,lastName,password,roles,enabled
                %s,Alice,Test,Password123!,ROLE_USER,true
                %s,Bob,Test,Password456!,ROLE_ADMIN;ROLE_USER,true
                """.formatted(email1, email2);

        BulkUserImportRequest request = new BulkUserImportRequest(
                csvData,
                null,
                "DefaultPass123!",
                Set.of("ROLE_USER"),
                true
        );

        mockMvc.perform(post("/api/admin/users/import")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(0));

        assertThat(userRepository.existsByEmail(email1)).isTrue();
        assertThat(userRepository.existsByEmail(email2)).isTrue();
    }

    @Test
    void importUsers_withExistingEmail_shouldSkipAndReportError() throws Exception {
        long ts = System.currentTimeMillis();
        String existingEmail = "seeded_admin@portalsso.local"; // or we can use admin@portalsso.local which is seeded
        String newEmail = "newimport_" + ts + "@example.com";

        String csvData = """
                email,firstName,lastName,password,roles,enabled
                admin@portalsso.local,Admin,Duplicate,Password123!,ROLE_USER,true
                %s,New,Guy,Password123!,ROLE_USER,true
                """.formatted(newEmail);

        BulkUserImportRequest request = new BulkUserImportRequest(
                csvData,
                null,
                "DefaultPass123!",
                Set.of("ROLE_USER"),
                true
        );

        mockMvc.perform(post("/api/admin/users/import")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.errors[0]").exists());

        assertThat(userRepository.existsByEmail(newEmail)).isTrue();
    }
}
