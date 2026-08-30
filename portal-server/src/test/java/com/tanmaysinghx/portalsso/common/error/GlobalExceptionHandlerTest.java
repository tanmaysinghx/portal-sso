package com.tanmaysinghx.portalsso.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.config.TestDataSeeder;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.util.UUID;
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
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void disablingSelfAccountReturnsBusinessRuleViolationWithPrtlCode() throws Exception {
        User admin = userRepository.findByEmail(TestDataSeeder.TEST_ADMIN_EMAIL).orElseThrow();

        mockMvc.perform(patch("/api/admin/users/" + admin.getId())
                        .with(user(TestDataSeeder.TEST_ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("PRTL-2002"))
                .andExpect(jsonPath("$.message").value("You can't disable your own account."))
                .andExpect(jsonPath("$.path").value("/api/admin/users/" + admin.getId()))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void nonExistentUserReturnsUserNotFoundWithPrtlCode() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/users/" + randomId)
                        .with(user(TestDataSeeder.TEST_ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("PRTL-2001"))
                .andExpect(jsonPath("$.message").value("No user found with ID: " + randomId))
                .andExpect(jsonPath("$.path").value("/api/admin/users/" + randomId));
    }

    @Test
    void duplicateOAuthClientReturnsConflictWithPrtlCode() throws Exception {
        String payload = """
                {
                    "clientId": "%s",
                    "clientName": "Duplicate Client",
                    "redirectUris": ["http://127.0.0.1:8080/cb"],
                    "scopes": ["openid"]
                }
                """.formatted(TestDataSeeder.TEST_CLIENT_ID);

        mockMvc.perform(post("/api/admin/oauth-clients")
                        .with(user(TestDataSeeder.TEST_ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("PRTL-3001"))
                .andExpect(jsonPath("$.message").value("client_id already exists: " + TestDataSeeder.TEST_CLIENT_ID))
                .andExpect(jsonPath("$.path").value("/api/admin/oauth-clients"));
    }

    @Test
    void invalidMethodArgumentReturnsValidationFailedWithFieldDetails() throws Exception {
        String invalidPayload = """
                {
                    "clientId": "",
                    "clientName": "",
                    "redirectUris": [],
                    "scopes": []
                }
                """;

        mockMvc.perform(post("/api/admin/oauth-clients")
                        .with(user(TestDataSeeder.TEST_ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("PRTL-4001"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").exists())
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void malformedJsonBodyReturnsMalformedRequestWithPrtlCode() throws Exception {
        mockMvc.perform(post("/api/admin/oauth-clients")
                        .with(user(TestDataSeeder.TEST_ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("PRTL-4002"))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable JSON request body."));
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowedWithPrtlCode() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/users")
                        .with(user(TestDataSeeder.TEST_ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("PRTL-4005"));
    }
}
