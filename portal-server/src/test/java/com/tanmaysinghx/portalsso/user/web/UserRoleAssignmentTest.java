package com.tanmaysinghx.portalsso.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.repository.AuditEventRepository;
import com.tanmaysinghx.portalsso.user.entity.Role;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The reason this endpoint exists: roles could only be set when an account was created, so a second
 * administrator could never be promoted through the product.
 *
 * <p>Most of these cover the refusals rather than the happy path. Every one of them is a way to make
 * the server unadministrable, and none of them can be undone from inside the product once they
 * succeed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserRoleAssignmentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @Transactional
    void anExistingUserCanBePromotedToAdministrator() throws Exception {
        String email = "promote_" + System.nanoTime() + "@example.com";
        String id = createUser(email, Set.of("ROLE_USER"));

        mockMvc.perform(put("/api/admin/users/" + id + "/roles")
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_ADMIN\",\"ROLE_USER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("ROLE_ADMIN", "ROLE_USER")));

        assertThat(userRepository.findByEmail(email).orElseThrow().getRoles())
                .extracting(Role::getName)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void aDemotionIsAppliedAndAudited() throws Exception {
        String email = "demote_" + System.nanoTime() + "@example.com";
        String id = createUser(email, Set.of("ROLE_ADMIN", "ROLE_USER"));

        mockMvc.perform(put("/api/admin/users/" + id + "/roles")
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_USER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.contains("ROLE_USER")));

        String details = auditEventRepository.findAll().stream()
                .filter(e -> e.getAction() == AuditAction.USER_ROLES_CHANGED && email.equals(e.getTargetLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the role change was not audited"))
                .getDetails();

        // A before/after diff, not just "roles changed" — the whole point of auditing a promotion.
        assertThat(details).contains("ROLE_ADMIN").contains("->");
    }

    /**
     * The likeliest accident, and the one that cannot be reversed by the person who made it: the
     * moment it succeeds they no longer have the access needed to undo it.
     */
    @Test
    void anAdministratorCannotRemoveTheirOwnAdminRole() throws Exception {
        String email = "selfdemote_" + System.nanoTime() + "@example.com";
        String id = createUser(email, Set.of("ROLE_ADMIN", "ROLE_USER"));

        mockMvc.perform(put("/api/admin/users/" + id + "/roles")
                        // Acting as the very account being demoted.
                        .with(user(email).roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_USER\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-2010"));

        assertThat(userRepository.findByEmail(email).orElseThrow().getRoles())
                .extracting(Role::getName)
                .contains("ROLE_ADMIN");
    }

    @Test
    void aRejectedRoleChangeIsNotAudited() throws Exception {
        String email = "noaudit_" + System.nanoTime() + "@example.com";
        String id = createUser(email, Set.of("ROLE_ADMIN", "ROLE_USER"));

        mockMvc.perform(put("/api/admin/users/" + id + "/roles")
                        .with(user(email).roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_USER\"]}"))
                .andExpect(status().isBadRequest());

        assertThat(auditEventRepository.findAll())
                .as("nothing may claim a refused demotion happened")
                .noneMatch(e -> e.getAction() == AuditAction.USER_ROLES_CHANGED && email.equals(e.getTargetLabel()));
    }

    /**
     * The guard that matters most, and the hardest to reach: the seeded admin and the accounts left
     * behind by the sibling tests mean there is normally more than one administrator. Every other
     * enabled admin is disabled first so the target really is the last one, and the whole test runs
     * in a transaction that rolls back — otherwise it would leave the shared database with no
     * administrator and take the rest of the suite with it.
     */
    @Test
    @Transactional
    void theLastEnabledAdministratorCannotBeDemoted() throws Exception {
        String email = "lastadmin_" + System.nanoTime() + "@example.com";
        String id = createUser(email, Set.of("ROLE_ADMIN", "ROLE_USER"));

        userRepository.findAllWithRoles().stream()
                .filter(u -> !u.getEmail().equals(email))
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_ADMIN")))
                .forEach(u -> {
                    u.setEnabled(false);
                    userRepository.save(u);
                });

        mockMvc.perform(put("/api/admin/users/" + id + "/roles")
                        // A third party, so the self-demotion rule is not what refuses this.
                        .with(user("someoneelse@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_USER\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-2009"));

        assertThat(userRepository.findByEmail(email).orElseThrow().getRoles())
                .extracting(Role::getName)
                .contains("ROLE_ADMIN");
    }

    /**
     * A <em>disabled</em> administrator cannot sign in, so counting them as cover would let the last
     * usable admin be demoted while the check still passed.
     */
    @Test
    @Transactional
    void aDisabledAdministratorDoesNotCountAsTheRemainingOne() throws Exception {
        String target = "target_" + System.nanoTime() + "@example.com";
        String targetId = createUser(target, Set.of("ROLE_ADMIN", "ROLE_USER"));
        String disabled = "disabledadmin_" + System.nanoTime() + "@example.com";
        createUser(disabled, Set.of("ROLE_ADMIN", "ROLE_USER"));

        // Everyone else off, leaving exactly one other admin — who is disabled.
        userRepository.findAllWithRoles().stream()
                .filter(u -> !u.getEmail().equals(target))
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_ADMIN")))
                .forEach(u -> {
                    u.setEnabled(false);
                    userRepository.save(u);
                });

        mockMvc.perform(put("/api/admin/users/" + targetId + "/roles")
                        .with(user("someoneelse@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_USER\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-2009"));
    }

    @Test
    void anUnknownRoleNameIsRejectedRatherThanSilentlySkipped() throws Exception {
        String email = "unknownrole_" + System.nanoTime() + "@example.com";
        String id = createUser(email, Set.of("ROLE_USER"));

        mockMvc.perform(put("/api/admin/users/" + id + "/roles")
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_USER\",\"ROLE_DOES_NOT_EXIST\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRTL-2006"));

        // The whole request fails, so the user is not left with a half-applied set.
        assertThat(userRepository.findByEmail(email).orElseThrow().getRoles())
                .extracting(Role::getName)
                .containsExactly("ROLE_USER");
    }

    /** An account with no roles signs in successfully and then fails every authorization check. */
    @Test
    void anEmptyRoleSetIsRejected() throws Exception {
        String email = "noroles_" + System.nanoTime() + "@example.com";
        String id = createUser(email, Set.of("ROLE_USER"));

        mockMvc.perform(put("/api/admin/users/" + id + "/roles")
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNonAdminCannotChangeRoles() throws Exception {
        String email = "nonadmin_" + System.nanoTime() + "@example.com";
        String id = createUser(email, Set.of("ROLE_USER"));

        mockMvc.perform(put("/api/admin/users/" + id + "/roles")
                        .with(user("someone@portalsso.local").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_ADMIN\"]}"))
                .andExpect(status().isForbidden());
    }

    private String createUser(String email, Set<String> roles) throws Exception {
        String body = mockMvc.perform(post("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateUserRequest(
                                email, "SecurePassword123!", null, null, roles, true))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get("id").asString();
    }
}
