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
import com.tanmaysinghx.portalsso.mail.entity.EmailSettings;
import com.tanmaysinghx.portalsso.mail.entity.EmailTemplateType;
import com.tanmaysinghx.portalsso.mail.entity.SmtpSettings;
import com.tanmaysinghx.portalsso.mail.entity.SmtpTransportSecurity;
import com.tanmaysinghx.portalsso.mail.repository.EmailSettingsRepository;
import com.tanmaysinghx.portalsso.mail.repository.SmtpSettingsRepository;
import com.tanmaysinghx.portalsso.mail.web.dto.SendTemplatePreviewRequest;
import com.tanmaysinghx.portalsso.mail.web.dto.UpdateEmailSettingsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Branding, the notification switches, and magic-link policy as the console drives them. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminEmailSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailSettingsRepository emailSettingsRepository;

    @Autowired
    private SmtpSettingsRepository smtpSettingsRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void reset() {
        emailSettingsRepository.deleteAll();
        smtpSettingsRepository.deleteAll();
    }


    /**
     * Leaves no configuration behind. These rows are server-wide and the Spring context is shared
     * across test classes, so a left-behind row with magic links enabled changes what unrelated
     * tests see — it already broke AuthPageControllerTest by rendering a second form on the
     * sign-in page.
     */
    @AfterEach
    void clearServerWideSettings() {
        emailSettingsRepository.deleteAll();
        smtpSettingsRepository.deleteAll();
    }

    private static UpdateEmailSettingsRequest request(
            String accentColor, boolean magicLink, int ttlMinutes) {
        return new UpdateEmailSettingsRequest(
                "Acme Identity",
                "https://cdn.example.com/logo.png",
                accentColor,
                "https://help.example.com",
                "Internal use only.",
                true,
                true,
                false,
                magicLink,
                false,
                ttlMinutes);
    }

    private void configureRelay() {
        SmtpSettings smtp = new SmtpSettings();
        smtp.setEnabled(true);
        smtp.setHost("127.0.0.1");
        smtp.setPort(1);
        smtp.setTransportSecurity(SmtpTransportSecurity.NONE);
        smtp.setFromAddress("portal@example.com");
        smtpSettingsRepository.save(smtp);
    }

    @Test
    void unauthenticatedAccessIsRefused() throws Exception {
        mockMvc.perform(get("/api/admin/email")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminAccessIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/email").with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theDefaultsAreTheSafeOnes() throws Exception {
        mockMvc.perform(get("/api/admin/email").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                // A welcome and a new-device alert are messages a user would be surprised NOT to
                // get; a mail on every sign-in and passwordless auth are opt-in decisions.
                .andExpect(jsonPath("$.welcomeEnabled").value(true))
                .andExpect(jsonPath("$.newDeviceEnabled").value(true))
                .andExpect(jsonPath("$.signInNoticeEnabled").value(false))
                .andExpect(jsonPath("$.magicLinkEnabled").value(false))
                .andExpect(jsonPath("$.magicLinkRegistrationEnabled").value(false))
                .andExpect(jsonPath("$.magicLinkTtlMinutes").value(15));

        // Reading does not create the row — the same rule as the SMTP settings.
        assertThat(emailSettingsRepository.count()).isZero();
    }

    @Test
    void theNotificationListComesFromTheServersOwnEnum() throws Exception {
        // So the console cannot hold a parallel copy of the message list that drifts from this one.
        mockMvc.perform(get("/api/admin/email").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(EmailTemplateType.values().length))
                .andExpect(jsonPath("$.notifications[0].type").value("WELCOME"))
                .andExpect(jsonPath("$.notifications[0].label").isNotEmpty())
                .andExpect(jsonPath("$.notifications[0].description").isNotEmpty());
    }

    @Test
    void anEnabledNotificationWithNoRelayReportsNoRelayRatherThanOn() throws Exception {
        // The state an operator who turned these on first is actually in. A checkbox cannot show
        // "on but silently undeliverable", so the status field names it.
        mockMvc.perform(get("/api/admin/email").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryReady").value(false))
                .andExpect(jsonPath("$.notifications[0].enabled").value(true))
                .andExpect(jsonPath("$.notifications[0].status").value("NO_RELAY"));
    }

    @Test
    void withARelayConfiguredAnEnabledNotificationReportsOn() throws Exception {
        configureRelay();
        mockMvc.perform(get("/api/admin/email").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryReady").value(true))
                .andExpect(jsonPath("$.notifications[0].status").value("ON"));
    }

    @Test
    void aDisabledNotificationReportsOffEvenWithARelay() throws Exception {
        configureRelay();
        mockMvc.perform(get("/api/admin/email").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                // SIGN_IN_NOTICE is third in the enum and off by default.
                .andExpect(jsonPath("$.notifications[2].type").value("SIGN_IN_NOTICE"))
                .andExpect(jsonPath("$.notifications[2].status").value("OFF"));
    }

    @Test
    void adminCanSaveAndReadBackSettings() throws Exception {
        mockMvc.perform(put("/api/admin/email")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("#2563EB", true, 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Acme Identity"))
                .andExpect(jsonPath("$.accentColor").value("#2563EB"))
                .andExpect(jsonPath("$.magicLinkEnabled").value(true))
                .andExpect(jsonPath("$.magicLinkTtlMinutes").value(20))
                .andExpect(jsonPath("$.updatedBy").value("admin@example.com"))
                // The portal URL is derived from the OIDC issuer, not stored, so links in mail are
                // absolute and reachable without a second thing to configure.
                .andExpect(jsonPath("$.portalUrl").isNotEmpty());

        mockMvc.perform(get("/api/admin/email").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Acme Identity"));
    }

    @Test
    void aNonHexAccentColourIsRejected() throws Exception {
        // It is interpolated into template markup, so an unchecked value here would be a
        // style-attribute injection into every message the server sends.
        for (String bad : new String[] {"red", "#12345", "#F59E0B; background:url(x)", "F59E0B"}) {
            mockMvc.perform(put("/api/admin/email")
                            .with(user("admin@example.com").roles("ADMIN"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request(bad, false, 15))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void aShorthandHexAccentColourIsAccepted() throws Exception {
        mockMvc.perform(put("/api/admin/email")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("#F80", false, 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accentColor").value("#F80"));
    }

    @Test
    void anExcessiveMagicLinkLifetimeIsRejected() throws Exception {
        // A sign-in link is a bearer credential sitting in a mailbox; a long window is the one
        // setting here that can quietly undo the feature's security.
        for (int ttl : new int[] {0, 61, 1440}) {
            mockMvc.perform(put("/api/admin/email")
                            .with(user("admin@example.com").roles("ADMIN"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request("#F59E0B", true, ttl))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void savingIsAuditedWithTheFieldsThatChanged() throws Exception {
        auditEventRepository.deleteAll();

        mockMvc.perform(put("/api/admin/email")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("#2563EB", true, 20))))
                .andExpect(status().isOk());

        var events = auditEventRepository.findAll().stream()
                .filter(e -> e.getAction() == AuditAction.EMAIL_SETTINGS_UPDATED)
                .toList();

        assertThat(events).hasSize(1);
        var event = events.getFirst();
        assertThat(event.getActorEmail()).isEqualTo("admin@example.com");
        assertThat(event.getTargetType()).isEqualTo(AuditAction.TargetType.SYSTEM);
        assertThat(event.getDetails()).contains("magic link sign-in enabled");
        // The link lifetime records its value, unlike a credential: it is a security parameter, and
        // a reviewer needs to know what it became.
        assertThat(event.getDetails()).contains("20 minutes");
    }

    @Test
    void aPreviewNeedsARelayAndSaysSoWhenThereIsNone() throws Exception {
        mockMvc.perform(post("/api/admin/email/preview")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendTemplatePreviewRequest(EmailTemplateType.WELCOME, "someone@example.com"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRTL-5002"));
    }

    @Test
    void aPreviewIsSentEvenForANotificationThatIsSwitchedOff() throws Exception {
        configureRelay();
        // The point of a preview is to see a message before deciding whether to turn it on. Needing
        // it on first would mean real users receive it during the review. The relay here is
        // unreachable, so this asserts the switch did not block it — the send still fails at the
        // relay, which is a 502 rather than the "not configured" 503 above.
        mockMvc.perform(post("/api/admin/email/preview")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendTemplatePreviewRequest(
                                        EmailTemplateType.SIGN_IN_NOTICE, "someone@example.com"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PRTL-5003"));
    }

    @Test
    void aPreviewToAMalformedAddressIsRejected() throws Exception {
        configureRelay();
        mockMvc.perform(post("/api/admin/email/preview")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendTemplatePreviewRequest(EmailTemplateType.WELCOME, "not-an-email"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNonAdminCannotSendAPreview() throws Exception {
        mockMvc.perform(post("/api/admin/email/preview")
                        .with(user("user@example.com").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendTemplatePreviewRequest(EmailTemplateType.WELCOME, "someone@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void savingDoesNotCreateASecondConfigurationRow() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(put("/api/admin/email")
                            .with(user("admin@example.com").roles("ADMIN"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request("#F59E0B", true, 15))))
                    .andExpect(status().isOk());
        }
        assertThat(emailSettingsRepository.count()).isEqualTo(1);
        assertThat(emailSettingsRepository.findByConfigKey(EmailSettings.SINGLETON_KEY)).isPresent();
    }
}
