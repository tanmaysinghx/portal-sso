package com.tanmaysinghx.portalsso.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.user.web.dto.CreateUserRequest;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs against its own database so the row count is known: the shared one accumulates users from
 * every other test class, which would make "page 2 has the right rows" unassertable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:userpaging;MODE=PostgreSQL",
        "app.seed.test-data=false"
})
class UserListPagingTest {

    @Autowired
    private MockMvc mockMvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private static boolean seeded;

    @BeforeEach
    void seedUsers() throws Exception {
        if (seeded) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            // Zero-padded so lexicographic order by email matches creation order.
            create("paged-%02d@example.com".formatted(i), i % 3 == 0 ? Set.of("ROLE_ADMIN") : Set.of("ROLE_USER"), i != 5);
        }
        seeded = true;
    }

    @Test
    void theListIsPagedRatherThanReturningEveryUser() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void pagesDoNotOverlapAndCoverEveryRow() throws Exception {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (int page = 0; page < 3; page++) {
            JsonNode body = jsonMapper.readTree(mockMvc.perform(get("/api/admin/users")
                            .with(user("admin@portalsso.local").roles("ADMIN"))
                            .param("size", "5").param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            body.get("content").forEach(n -> seen.add(n.get("email").asString()));
        }
        // A duplicate here would mean unstable ordering between pages — a row shown twice while
        // another is never shown at all.
        assertThat(seen).hasSize(12);
    }

    /** The roles association has to survive the two-query paging path, not come back empty. */
    @Test
    void pagedRowsStillCarryTheirRoles() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].roles").isNotEmpty());
    }

    @Test
    void searchMatchesOnEmail() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .param("search", "paged-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("paged-07@example.com"));
    }

    /** A stray % in a search term must be a literal, not a wildcard matching everything. */
    @Test
    void aWildcardInTheSearchTermIsTreatedLiterally() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .param("search", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filteringByEnabledNarrowsTheResult() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("paged-05@example.com"));
    }

    /**
     * Role filtering uses an EXISTS subquery rather than a join. A join would emit one row per role
     * for multi-role users, inflating totalElements and paginating over duplicates.
     */
    @Test
    void filteringByRoleCountsEachUserOnce() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .param("role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content.length()").value(4));
    }

    @Test
    void anOversizedPageIsClamped() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200));
    }

    private void create(String email, Set<String> roles, boolean enabled) throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateUserRequest(
                                email, "SecurePassword123!", null, null, roles, enabled))))
                .andExpect(status().isCreated());
    }
}
