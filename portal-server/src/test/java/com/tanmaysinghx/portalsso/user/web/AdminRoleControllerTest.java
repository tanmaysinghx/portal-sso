package com.tanmaysinghx.portalsso.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
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
class AdminRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleRepository roleRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * Migration 011 seeds these. Before it, a production deployment had an empty roles table and no
     * way to assign or create one, so this asserts the bootstrap actually happened.
     */
    @Test
    void thePlatformRolesExistWithoutTheDevSeeder() {
        assertThat(roleRepository.findByName("ROLE_ADMIN")).isPresent();
        assertThat(roleRepository.findByName("ROLE_USER")).isPresent();
    }

    @Test
    void rolesAreListedWithTheirUserCountsAndProtectedFlag() throws Exception {
        mockMvc.perform(get("/api/admin/roles").with(user("admin@portalsso.local").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'ROLE_ADMIN')].protectedRole").value(true))
                .andExpect(jsonPath("$[?(@.name == 'ROLE_ADMIN')].userCount").exists());
    }

    @Test
    void aNonAdminCannotReachTheRoleRegistry() throws Exception {
        mockMvc.perform(get("/api/admin/roles").with(user("user@portalsso.local").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aRoleCanBeCreatedAndItsDescriptionEdited() throws Exception {
        String name = "ROLE_SUPPORT_" + System.nanoTime();

        String body = mockMvc.perform(post("/api/admin/roles")
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new java.util.HashMap<>(java.util.Map.of(
                                "name", name, "description", "Support desk")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.userCount").value(0))
                .andExpect(jsonPath("$.protectedRole").value(false))
                .andReturn().getResponse().getContentAsString();

        String id = jsonMapper.readTree(body).get("id").asString();

        mockMvc.perform(put("/api/admin/roles/" + id)
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Tier 2 support\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Tier 2 support"))
                // The name is not editable and must survive an update untouched.
                .andExpect(jsonPath("$.name").value(name));
    }

    /**
     * A role name becomes the granted authority verbatim, and {@code hasRole('X')} looks for
     * {@code ROLE_X}. Without the prefix the role would be assignable and completely inert — a
     * permission that looks granted and silently is not.
     */
    @Test
    void aRoleNameWithoutTheRolePrefixIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/roles")
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"EDITOR\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDuplicateRoleNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/roles")
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ROLE_ADMIN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRTL-2007"));
    }

    /**
     * user_roles cascades on delete, so removing ROLE_ADMIN would demote every administrator in one
     * statement and leave nobody able to sign in and undo it.
     */
    @Test
    void thePlatformRolesCannotBeDeleted() throws Exception {
        String adminRoleId = roleRepository.findByName("ROLE_ADMIN").orElseThrow().getId().toString();

        mockMvc.perform(delete("/api/admin/roles/" + adminRoleId)
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-2008"));

        assertThat(roleRepository.findByName("ROLE_ADMIN")).as("must still exist").isPresent();
    }

    @Test
    void anOperatorDefinedRoleCanBeDeleted() throws Exception {
        String name = "ROLE_TEMPORARY_" + System.nanoTime();

        String body = mockMvc.perform(post("/api/admin/roles")
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = jsonMapper.readTree(body).get("id").asString();

        mockMvc.perform(delete("/api/admin/roles/" + id)
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(roleRepository.findByName(name)).isEmpty();
    }

    @Test
    void deletingAnUnknownRoleIsANotFound() throws Exception {
        mockMvc.perform(delete("/api/admin/roles/" + java.util.UUID.randomUUID())
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRTL-2006"));
    }
}
