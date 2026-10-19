package com.tanmaysinghx.portalsso.mail.web;

import com.tanmaysinghx.portalsso.mail.service.SmtpSettingsService;
import com.tanmaysinghx.portalsso.mail.web.dto.SendTestEmailRequest;
import com.tanmaysinghx.portalsso.mail.web.dto.SendTestEmailResponse;
import com.tanmaysinghx.portalsso.mail.web.dto.SmtpSettingsResponse;
import com.tanmaysinghx.portalsso.mail.web.dto.UpdateSmtpSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-wide outbound email configuration.
 *
 * <p>Administrator-only, like every other {@code /api/admin} route: a relay is somewhere this
 * server will send mail from its own identity, and the stored credentials belong to the
 * organisation rather than to any one account.
 */
@RestController
@RequestMapping("/api/admin/smtp")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSmtpController {

    private final SmtpSettingsService smtpSettingsService;

    public AdminSmtpController(SmtpSettingsService smtpSettingsService) {
        this.smtpSettingsService = smtpSettingsService;
    }

    /** The stored configuration. Never includes the password — see {@link SmtpSettingsResponse}. */
    @GetMapping
    public ResponseEntity<SmtpSettingsResponse> getSettings() {
        return ResponseEntity.ok(smtpSettingsService.getSettings());
    }

    @PutMapping
    public ResponseEntity<SmtpSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateSmtpSettingsRequest request) {
        return ResponseEntity.ok(smtpSettingsService.save(request));
    }

    /**
     * Sends one test message, optionally against settings that have not been saved yet.
     *
     * <p>A relay that refuses the message answers the question the caller asked, so that comes back
     * as {@code 200} with {@code success=false} and the reason. Only a genuine fault — no
     * configuration at all, or an incomplete one — is a non-2xx response.
     */
    @PostMapping("/test")
    public ResponseEntity<SendTestEmailResponse> sendTestEmail(
            @Valid @RequestBody SendTestEmailRequest request) {
        return ResponseEntity.ok(smtpSettingsService.sendTestEmail(request));
    }
}
