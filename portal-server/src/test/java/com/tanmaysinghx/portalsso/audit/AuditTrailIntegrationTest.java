package com.tanmaysinghx.portalsso.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.entity.AuditEvent;
import com.tanmaysinghx.portalsso.audit.repository.AuditEventRepository;
import com.tanmaysinghx.portalsso.client.web.dto.CreateOAuthClientRequest;
import com.tanmaysinghx.portalsso.user.web.dto.CreateUserRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * These assert the property the audit log exists for: that a privileged change cannot happen without
 * leaving a record. Testing the controller and the writer separately would miss exactly the failure
 * that matters — a new endpoint that simply forgets to call {@code AuditService}.
 *
 * <p>Top-level class rather than {@code @Nested} inside a shared parent: {@code @Nested} with
 * {@code @SpringBootTest} silently runs zero tests while still reporting success.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditTrailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void creatingAUserRecordsWhoDidItAndWhatTheyGranted() throws Exception {
        String email = "audit_create_" + System.nanoTime() + "@example.com";

        mockMvc.perform(post("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateUserRequest(
                                email, "SecurePassword123!", "Aud", "Itor", Set.of("ROLE_ADMIN", "ROLE_USER"), true))))
                .andExpect(status().isCreated());

        AuditEvent event = requireEvent(AuditAction.USER_CREATED, email);
        assertThat(event.getActorEmail()).isEqualTo("admin@portalsso.local");
        assertThat(event.getTargetType()).isEqualTo(AuditAction.TargetType.USER);
        assertThat(event.getTargetId()).isNotBlank();
        assertThat(event.getDetails()).contains("ROLE_ADMIN");
    }

    /**
     * The audit trail is read by more people than the users table is, so a credential leaking into
     * it would widen the blast radius of an otherwise ordinary read. Asserted rather than assumed,
     * because the natural way to write a "details" string is to dump the whole request.
     */
    @Test
    void theRecordedDetailsNeverContainTheSubmittedPassword() throws Exception {
        String email = "audit_secret_" + System.nanoTime() + "@example.com";
        String password = "UniqueEnoughToGrepFor" + System.nanoTime() + "!";

        mockMvc.perform(post("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateUserRequest(
                                email, password, null, null, Set.of("ROLE_USER"), true))))
                .andExpect(status().isCreated());

        assertThat(auditEventRepository.findAll())
                .as("no audit row may carry a password in any field")
                .noneMatch(e -> (e.getDetails() != null && e.getDetails().contains(password))
                        || (e.getTargetLabel() != null && e.getTargetLabel().contains(password)));
    }

    @Test
    void disablingAndReEnablingAUserAreRecordedAsDistinctActions() throws Exception {
        String email = "audit_toggle_" + System.nanoTime() + "@example.com";
        String userId = createUser(email);

        mockMvc.perform(patch("/api/admin/users/" + userId)
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/" + userId)
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        assertThat(requireEvent(AuditAction.USER_DISABLED, email).getTargetId()).isEqualTo(userId);
        assertThat(requireEvent(AuditAction.USER_ENABLED, email).getTargetId()).isEqualTo(userId);
    }

    @Test
    void unlockingAnAccountRecordsHowManyFailedAttemptsWereCleared() throws Exception {
        String email = "audit_unlock_" + System.nanoTime() + "@example.com";
        String userId = createUser(email);

        mockMvc.perform(post("/api/admin/users/" + userId + "/unlock")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(requireEvent(AuditAction.USER_UNLOCKED, email).getDetails())
                .isEqualTo("clearedFailedAttempts=0");
    }

    @Test
    void registeringAndDeletingAnOAuthClientAreBothRecorded() throws Exception {
        String clientId = "audit-client-" + System.nanoTime();

        String body = mockMvc.perform(post("/api/admin/oauth-clients")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateOAuthClientRequest(
                                clientId,
                                "Audit Client",
                                List.of("https://example.com/callback"),
                                List.of("openid", "profile"),
                                null,
                                false,
                                false))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        AuditEvent created = requireEvent(AuditAction.CLIENT_CREATED, clientId);
        assertThat(created.getDetails())
                .as("a redirect URI is how a client becomes an exfiltration path; it must be recorded")
                .contains("https://example.com/callback");

        // The create response now nests the client so a secret field cannot leak into the list DTO.
        String id = jsonMapper.readTree(body).get("client").get("id").asString();
        mockMvc.perform(delete("/api/admin/oauth-clients/" + id)
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // The client row is gone; the audit row must still identify what was deleted.
        AuditEvent deleted = requireEvent(AuditAction.CLIENT_DELETED, clientId);
        assertThat(deleted.getTargetLabel()).isEqualTo(clientId);
        assertThat(deleted.getDetails()).contains("revokedGrants=");
    }

    /**
     * The audit write joins the caller's transaction, so an operation that is rejected leaves no
     * entry. A log containing changes that never happened would be worse than one with gaps — this
     * pins the behaviour that makes it trustworthy.
     */
    @Test
    void aRejectedChangeLeavesNoAuditEntry() throws Exception {
        String email = "audit_selfdisable_" + System.nanoTime() + "@example.com";
        String userId = createUser(email);

        mockMvc.perform(patch("/api/admin/users/" + userId)
                        // Acting as the account being disabled, which the controller refuses.
                        .with(user(email).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());

        assertThat(findEvent(AuditAction.USER_DISABLED, email))
                .as("the change was refused, so nothing may claim it happened")
                .isEmpty();
    }

    private String createUser(String email) throws Exception {
        String body = mockMvc.perform(post("/api/admin/users")
                        .with(user("admin@portalsso.local").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateUserRequest(
                                email, "SecurePassword123!", null, null, Set.of("ROLE_USER"), true))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode node = jsonMapper.readTree(body);
        return node.get("id").asString();
    }

    private AuditEvent requireEvent(AuditAction action, String targetLabel) {
        return findEvent(action, targetLabel)
                .orElseThrow(() -> new AssertionError(
                        "no %s audit entry was recorded for '%s'".formatted(action, targetLabel)));
    }

    private Optional<AuditEvent> findEvent(AuditAction action, String targetLabel) {
        return auditEventRepository.findAll().stream()
                .filter(e -> e.getAction() == action && targetLabel.equals(e.getTargetLabel()))
                .findFirst();
    }
}
