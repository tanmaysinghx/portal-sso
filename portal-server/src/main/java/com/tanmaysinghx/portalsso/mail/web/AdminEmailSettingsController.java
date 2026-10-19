package com.tanmaysinghx.portalsso.mail.web;

import com.tanmaysinghx.portalsso.mail.service.EmailSettingsService;
import com.tanmaysinghx.portalsso.mail.service.NotificationService;
import com.tanmaysinghx.portalsso.mail.web.dto.EmailSettingsResponse;
import com.tanmaysinghx.portalsso.mail.web.dto.SendTemplatePreviewRequest;
import com.tanmaysinghx.portalsso.mail.web.dto.UpdateEmailSettingsRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Branding, which notifications are sent, and magic-link policy.
 *
 * <p>Separate from {@code AdminSmtpController} along the same seam as the two tables behind them:
 * that one is how mail is delivered, this one is what gets sent and how it looks.
 */
@RestController
@RequestMapping("/api/admin/email")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmailSettingsController {

    private final EmailSettingsService emailSettingsService;
    private final NotificationService notificationService;

    public AdminEmailSettingsController(
            EmailSettingsService emailSettingsService, NotificationService notificationService) {
        this.emailSettingsService = emailSettingsService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<EmailSettingsResponse> getSettings() {
        return ResponseEntity.ok(emailSettingsService.getSettings());
    }

    @PutMapping
    public ResponseEntity<EmailSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateEmailSettingsRequest request) {
        return ResponseEntity.ok(emailSettingsService.save(request));
    }

    /**
     * Sends one template, filled with sample data, to an address.
     *
     * <p>Ignores the notification switches on purpose: the point is to see a message before
     * deciding whether to turn it on, and requiring it to be on first would mean real users receive
     * it during the review. Unlike the SMTP test, a failure here is a real error rather than a
     * {@code 200} with a flag — the relay was already proven on the SMTP tab, so a failure at this
     * point is a fault, not an answer.
     */
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> sendPreview(
            @Valid @RequestBody SendTemplatePreviewRequest request) {
        notificationService.sendPreview(request.type(), request.recipient());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Preview of \"" + request.type().getLabel() + "\" sent to " + request.recipient() + "."));
    }
}
