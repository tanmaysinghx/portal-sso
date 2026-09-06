package com.tanmaysinghx.portalsso.setup.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanmaysinghx.portalsso.setup.web.SetupController.SetupInitializeRequest;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
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
class SetupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Test
    void getSetupStatusReturnsCurrentStatus() throws Exception {
        mockMvc.perform(get("/api/public/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").isBoolean())
                .andExpect(jsonPath("$.databaseType").isNotEmpty());
    }

    @Test
    void cannotInitializeWhenAdminAlreadyExists() throws Exception {
        // Test context already has an admin seeded
        SetupInitializeRequest request = new SetupInitializeRequest(
                "newadmin@example.com",
                "SecureAdminPass123!",
                "First",
                "Last");

        mockMvc.perform(post("/api/public/setup/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
