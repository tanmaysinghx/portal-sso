package com.tanmaysinghx.portalsso.mail.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.repository.AuditEventRepository;
import com.tanmaysinghx.portalsso.mail.entity.SmtpSettings;
import com.tanmaysinghx.portalsso.mail.entity.SmtpTransportSecurity;
import com.tanmaysinghx.portalsso.mail.repository.SmtpSettingsRepository;
import com.tanmaysinghx.portalsso.mail.web.dto.SendTestEmailRequest;
import com.tanmaysinghx.portalsso.mail.web.dto.UpdateSmtpSettingsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers the admin-configurable SMTP settings end to end: who may read and write them, what is
 * required only once email is switched on, and the rule that the stored password never comes back
 * out.
 *
 * <p>Test sends point at {@code 127.0.0.1:1}, where nothing listens. That is deliberate — it
 * exercises the real failure path with no network dependency and no SMTP server to stand up, and
 * connection-refused is immediate rather than waiting out the timeout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSmtpControllerTest {

    private static final String UNREACHABLE_HOST = "127.0.0.1";
    private static final int UNREACHABLE_PORT = 1;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SmtpSettingsRepository smtpSettingsRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clearSettings() {
        smtpSettingsRepository.deleteAll();
    }

    private static UpdateSmtpSettingsRequest complete(String password) {
        return new UpdateSmtpSettingsRequest(
                true,
                UNREACHABLE_HOST,
                UNREACHABLE_PORT,
                SmtpTransportSecurity.STARTTLS,
                "relay-user",
                password,
                "portal@example.com",
                "Portal SSO",
                null);
    }

    @Test
    void unauthenticatedAccessIsRefused() throws Exception {
        mockMvc.perform(get("/api/admin/smtp"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminAccessIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/smtp")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void beforeAnythingIsConfiguredTheSettingsReadAsDisabled() throws Exception {
        mockMvc.perform(get("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.usable").value(false))
                .andExpect(jsonPath("$.passwordSet").value(false))
                .andExpect(jsonPath("$.transportSecurity").value("STARTTLS"));

        // Reading must not create the row: a server where nobody opened the email tab should look
        // untouched, and MailService.isEnabled() runs on paths that may not be able to write.
        assertThat(smtpSettingsRepository.count()).isZero();
    }

    @Test
    void adminCanSaveAndReadBackSettings() throws Exception {
        mockMvc.perform(put("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complete("relay-secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.usable").value(true))
                .andExpect(jsonPath("$.host").value(UNREACHABLE_HOST))
                .andExpect(jsonPath("$.port").value(UNREACHABLE_PORT))
                .andExpect(jsonPath("$.username").value("relay-user"))
                .andExpect(jsonPath("$.fromAddress").value("portal@example.com"))
                .andExpect(jsonPath("$.updatedBy").value("admin@example.com"));

        mockMvc.perform(get("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value(UNREACHABLE_HOST))
                .andExpect(jsonPath("$.passwordSet").value(true));
    }

    @Test
    void theStoredPasswordIsNeverReturnedAndNeverStoredInPlaintext() throws Exception {
        mockMvc.perform(put("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complete("relay-secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordSet").value(true))
                // Not merely masked — absent. A fixed mask would still leak that a password exists
                // in a field the console might echo back on save.
                .andExpect(jsonPath("$.password").doesNotExist());

        SmtpSettings stored = smtpSettingsRepository
                .findByConfigKey(SmtpSettings.SINGLETON_KEY)
                .orElseThrow();
        assertThat(stored.getPasswordEncrypted()).isNotNull().isNotEqualTo("relay-secret");
        assertThat(stored.getPasswordEncrypted()).doesNotContain("relay-secret");
    }

    @Test
    void omittingThePasswordKeepsTheStoredOne() throws Exception {
        mockMvc.perform(put("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complete("relay-secret"))))
                .andExpect(status().isOk());

        String ciphertext = smtpSettingsRepository
                .findByConfigKey(SmtpSettings.SINGLETON_KEY)
                .orElseThrow()
                .getPasswordEncrypted();

        // The console sends null when the administrator edits another field without retyping the
        // secret, which is the common case once a relay is working.
        mockMvc.perform(put("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complete(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordSet").value(true));

        assertThat(smtpSettingsRepository
                        .findByConfigKey(SmtpSettings.SINGLETON_KEY)
                        .orElseThrow()
                        .getPasswordEncrypted())
                .isEqualTo(ciphertext);
    }

    @Test
    void anEmptyPasswordClearsTheStoredOne() throws Exception {
        mockMvc.perform(put("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complete("relay-secret"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complete(""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordSet").value(false));

        assertThat(smtpSettingsRepository
                        .findByConfigKey(SmtpSettings.SINGLETON_KEY)
                        .orElseThrow()
                        .getPasswordEncrypted())
                .isNull();
    }

    @Test
    void anIncompleteConfigurationCanBeSavedWhileEmailIsOff() throws Exception {
        // Half-filled is a normal intermediate state while someone looks up their provider's
        // settings. Losing it on save would be worse than storing something that cannot yet send.
        UpdateSmtpSettingsRequest partial = new UpdateSmtpSettingsRequest(
                false, "smtp.example.com", null, SmtpTransportSecurity.STARTTLS,
                null, null, null, null, null);

        mockMvc.perform(put("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(partial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.usable").value(false))
                .andExpect(jsonPath("$.host").value("smtp.example.com"));
    }

    @Test
    void enablingEmailWithoutTheFieldsASendNeedsIsRejected() throws Exception {
        UpdateSmtpSettingsRequest enabledButEmpty = new UpdateSmtpSettingsRequest(
                true, null, null, SmtpTransportSecurity.STARTTLS, null, null, null, null, null);

        mockMvc.perform(put("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enabledButEmpty)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-4006"));
    }

    @Test
    void anInvalidFromAddressIsRejected() throws Exception {
        UpdateSmtpSettingsRequest badFrom = new UpdateSmtpSettingsRequest(
                true, UNREACHABLE_HOST, UNREACHABLE_PORT, SmtpTransportSecurity.STARTTLS,
                null, null, "not-an-email", null, null);

        mockMvc.perform(put("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badFrom)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPortOutsideTheValidRangeIsRejected() throws Exception {
        UpdateSmtpSettingsRequest badPort = new UpdateSmtpSettingsRequest(
                true, UNREACHABLE_HOST, 70000, SmtpTransportSecurity.STARTTLS,
                null, null, "portal@example.com", null, null);

        mockMvc.perform(put("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPort)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savingTheSettingsIsAuditedWithoutRecordingThePassword() throws Exception {
        auditEventRepository.deleteAll();

        mockMvc.perform(put("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complete("relay-secret"))))
                .andExpect(status().isOk());

        var events = auditEventRepository.findAll().stream()
                .filter(e -> e.getAction() == AuditAction.SMTP_SETTINGS_UPDATED)
                .toList();

        assertThat(events).hasSize(1);
        var event = events.getFirst();
        assertThat(event.getActorEmail()).isEqualTo("admin@example.com");
        assertThat(event.getTargetType()).isEqualTo(AuditAction.TargetType.SYSTEM);
        assertThat(event.getDetails()).contains("password changed");
        // The whole point: the log says which fields moved, never what they moved to.
        assertThat(event.getDetails()).doesNotContain("relay-secret");
        assertThat(event.getTargetLabel()).doesNotContain("relay-secret");
    }

    @Test
    void aTestSendAgainstAnUnreachableRelayReportsFailureRatherThanErroring() throws Exception {
        SendTestEmailRequest request =
                new SendTestEmailRequest("someone@example.com", complete("relay-secret"));

        // A relay refusing the message answers the question that was asked, so it is a 200 with
        // success=false. Only a genuine fault is a non-2xx.
        mockMvc.perform(post("/api/admin/smtp/test")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void testingUnsavedSettingsDoesNotCreateAConfiguration() throws Exception {
        SendTestEmailRequest request =
                new SendTestEmailRequest("someone@example.com", complete("relay-secret"));

        mockMvc.perform(post("/api/admin/smtp/test")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertThat(smtpSettingsRepository.count()).isZero();
    }

    @Test
    void aFailedTestAgainstSavedSettingsIsRecordedOnTheRow() throws Exception {
        mockMvc.perform(put("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complete("relay-secret"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/smtp/test")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendTestEmailRequest("someone@example.com", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastTestSuccessful").value(false))
                .andExpect(jsonPath("$.lastTestAt").isNotEmpty())
                .andExpect(jsonPath("$.lastTestError").isNotEmpty());
    }

    @Test
    void aTestWithNeitherStoredNorSubmittedSettingsIsRefused() throws Exception {
        mockMvc.perform(post("/api/admin/smtp/test")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendTestEmailRequest("someone@example.com", null))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRTL-5002"));
    }

    @Test
    void aTestWithoutARecipientIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/smtp/test")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendTestEmailRequest("", complete("relay-secret")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNonAdminCannotSendATestEmail() throws Exception {
        mockMvc.perform(post("/api/admin/smtp/test")
                        .with(user("user@example.com").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendTestEmailRequest("someone@example.com", complete("x")))))
                .andExpect(status().isForbidden());
    }
}
