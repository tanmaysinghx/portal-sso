package com.tanmaysinghx.portalsso.application.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.application.entity.ApplicationAccessType;
import com.tanmaysinghx.portalsso.application.repository.ApplicationRepository;
import com.tanmaysinghx.portalsso.application.web.dto.CreateApplicationRequest;
import com.tanmaysinghx.portalsso.application.web.dto.UpdateApplicationRequest;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private RoleRepository roleRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
    }

    @Test
    void nonAdminCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/applications")
                        .with(user("regular@portalsso.local").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotAccessUserApplications() throws Exception {
        mockMvc.perform(get("/api/user/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCreateAndListApplications() throws Exception {
        CreateApplicationRequest request = new CreateApplicationRequest(
                "Grafana Dashboard",
                "Observability metrics and alerts",
                "https://grafana.internal",
                "https://grafana.internal/logo.png",
                "Engineering",
                null,
                ApplicationAccessType.ALL_USERS,
                List.of(),
                true,
                1);

        mockMvc.perform(post("/api/admin/applications")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Grafana Dashboard"))
                .andExpect(jsonPath("$.appUrl").value("https://grafana.internal"))
                .andExpect(jsonPath("$.category").value("Engineering"))
                .andExpect(jsonPath("$.accessType").value("ALL_USERS"));

        mockMvc.perform(get("/api/admin/applications")
                        .with(user("admin@portalsso.local").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Grafana Dashboard"));
    }

    @Test
    void userReceivesOnlyPermittedAndEnabledApplications() throws Exception {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();

        // App 1: Public to all users
        CreateApplicationRequest app1 = new CreateApplicationRequest(
                "Company Wiki",
                "Knowledge base",
                "https://wiki.corp",
                null,
                "Productivity",
                null,
                ApplicationAccessType.ALL_USERS,
                List.of(),
                true,
                1);

        // App 2: Restricted to ROLE_ADMIN
        CreateApplicationRequest app2 = new CreateApplicationRequest(
                "Production Cluster",
                "Kubernetes management",
                "https://k8s.internal",
                null,
                "Operations",
                null,
                ApplicationAccessType.RESTRICTED,
                List.of(adminRole.getId()),
                true,
                2);

        // App 3: Public to all but disabled
        CreateApplicationRequest app3 = new CreateApplicationRequest(
                "Old Portal",
                "Deprecated portal",
                "https://legacy.internal",
                null,
                "General",
                null,
                ApplicationAccessType.ALL_USERS,
                List.of(),
                false,
                3);

        mockMvc.perform(post("/api/admin/applications")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(app1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/applications")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(app2)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/applications")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(app3)))
                .andExpect(status().isCreated());

        // Regular user should see only "Company Wiki"
        mockMvc.perform(get("/api/user/applications")
                        .with(user("alice@corp.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Company Wiki"));

        // Admin user should see both enabled apps ("Company Wiki" and "Production Cluster"), but not disabled
        mockMvc.perform(get("/api/user/applications")
                        .with(user("admin@corp.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void adminCanUpdateAndDeleteApplication() throws Exception {
        CreateApplicationRequest createReq = new CreateApplicationRequest(
                "Jira",
                "Issue tracking",
                "https://jira.internal",
                null,
                "General",
                null,
                ApplicationAccessType.ALL_USERS,
                List.of(),
                true,
                1);

        String createRes = mockMvc.perform(post("/api/admin/applications")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID appId = UUID.fromString(jsonMapper.readTree(createRes).get("id").asText());

        UpdateApplicationRequest updateReq = new UpdateApplicationRequest(
                "Jira Software",
                "Project management and issue tracking",
                "https://jira.internal/secure",
                "https://jira.internal/icon.svg",
                "Engineering",
                null,
                ApplicationAccessType.ALL_USERS,
                List.of(),
                true,
                1);

        mockMvc.perform(put("/api/admin/applications/" + appId)
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jira Software"))
                .andExpect(jsonPath("$.category").value("Engineering"));

        mockMvc.perform(delete("/api/admin/applications/" + appId)
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(applicationRepository.findById(appId)).isEmpty();
    }
}
