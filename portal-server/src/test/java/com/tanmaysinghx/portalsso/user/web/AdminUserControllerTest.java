package com.tanmaysinghx.portalsso.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.web.dto.CreateUserRequest;
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
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void createUser_whenAdmin_shouldCreateAndReturnUser() throws Exception {
        String email = "newuser_" + System.currentTimeMillis() + "@example.com";
        CreateUserRequest request = new CreateUserRequest(
                email,
                "SecurePassword123!",
                "John",
                "Doe",
                Set.of("ROLE_ADMIN", "ROLE_USER"),
                true
        );

        mockMvc.perform(post("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.roles[0]").exists());

        assertThat(userRepository.existsByEmail(email)).isTrue();
    }

    @Test
    void createUser_whenDuplicateEmail_shouldReturn409Conflict() throws Exception {
        String email = "duplicate_" + System.currentTimeMillis() + "@example.com";
        CreateUserRequest request = new CreateUserRequest(
                email,
                "SecurePassword123!",
                "Jane",
                "Doe",
                Set.of("ROLE_USER"),
                true
        );

        // First creation
        mockMvc.perform(post("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate creation attempt
        mockMvc.perform(post("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRTL-2003"));
    }

    @Test
    void createUser_whenNonAdmin_shouldReturn403Forbidden() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "unauthorized@example.com",
                "SecurePassword123!",
                "User",
                "Role",
                Set.of("ROLE_USER"),
                true
        );

        mockMvc.perform(post("/api/admin/users")
                        .with(user("user@portalsso.local").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
