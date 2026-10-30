package com.tanmaysinghx.portalsso.setup.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanmaysinghx.portalsso.setup.SetupKeyService;
import com.tanmaysinghx.portalsso.setup.web.SetupController.SetupInitializeRequest;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The wizard on a server that already has an administrator (the suite seeds one). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SetupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SetupKeyService setupKeyService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void statusReportsSetupCompleteAndHidesTheKeyFile() throws Exception {
        mockMvc.perform(get("/api/public/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(false))
                .andExpect(jsonPath("$.setupKeyIssued").value(false))
                .andExpect(jsonPath("$.setupKeyFile").doesNotExist())
                .andExpect(jsonPath("$.databaseType").isNotEmpty());
    }

    @Test
    void cannotInitializeWhenAdminAlreadyExistsEvenWithAValidKey() throws Exception {
        // Sent with a CSRF token and a real key, so the refusal can only come from the "already set
        // up" rule. Without the token this test used to pass on a CSRF 403 and prove nothing.
        String key = setupKeyService.issue();
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/api/public/setup/initialize")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetupInitializeRequest(
                                key, "newadmin@example.com", "SecureAdminPass123!", "First", "Last"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRTL-1011"));

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(userRepository.findByEmail("newadmin@example.com")).isEmpty();
        assertThat(setupKeyService.consume(key)).as("a refused request must not spend the key").isTrue();
    }

    @Test
    void initializeIsCsrfProtected() throws Exception {
        mockMvc.perform(post("/api/public/setup/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetupInitializeRequest(
                                "key", "newadmin@example.com", "SecureAdminPass123!", null, null))))
                .andExpect(status().isForbidden());
    }
}
