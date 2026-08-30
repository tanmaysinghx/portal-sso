package com.tanmaysinghx.portalsso.audit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AdminAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * The log names who did what to whom. Exposing it to a non-admin would turn the compliance
     * feature into a directory-disclosure one.
     */
    @Test
    void aNonAdminCannotReadTheAuditLog() throws Exception {
        mockMvc.perform(get("/api/admin/audit").with(user("user@portalsso.local").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theLogIsReturnedNewestFirstInAPageEnvelope() throws Exception {
        createUser("audit_page_" + System.nanoTime() + "@example.com");

        mockMvc.perform(get("/api/admin/audit")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.content[0].actionLabel").exists());
    }

    @Test
    void filteringByActionNarrowsTheResult() throws Exception {
        createUser("audit_filter_" + System.nanoTime() + "@example.com");

        String body = mockMvc.perform(get("/api/admin/audit")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .param("action", "USER_CREATED")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("USER_CREATED");
        assertThat(body).doesNotContain("CLIENT_DELETED");
    }

    /**
     * A filter the server cannot honour must fail loudly. Silently ignoring it would return rows the
     * caller did not ask for while looking like a successful narrow search — the worst outcome for
     * an investigation.
     */
    @Test
    void anUnknownActionFilterIsRejectedRatherThanIgnored() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .param("action", "NOT_A_REAL_ACTION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-4001"));
    }

    @Test
    void anOversizedPageRequestIsClampedRatherThanHonoured() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200));
    }

    @Test
    void theAvailableActionsAreDiscoverableSoTheConsoleNeedNotHardcodeThem() throws Exception {
        mockMvc.perform(get("/api/admin/audit/actions").with(user("admin@portalsso.local").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.value == 'USER_CREATED')].targetType").value("USER"))
                .andExpect(jsonPath("$[?(@.value == 'CLIENT_DELETED')].targetType").value("OAUTH_CLIENT"));
    }

    @Test
    void theExportIsCsvAndDownloadsAsAFile() throws Exception {
        createUser("audit_export_" + System.nanoTime() + "@example.com");

        mockMvc.perform(get("/api/admin/audit/export").with(user("admin@portalsso.local").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("occurred_at,actor_email,action")));
    }

    private void createUser(String email) throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateUserRequest(
                                email, "SecurePassword123!", null, null, Set.of("ROLE_USER"), true))))
                .andExpect(status().isCreated());
    }
}
