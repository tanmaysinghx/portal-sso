package com.tanmaysinghx.portalsso.analytics.retention.web;

import com.tanmaysinghx.portalsso.analytics.retention.service.AnalyticsRetentionSettingsService;
import com.tanmaysinghx.portalsso.analytics.retention.web.dto.RetentionSettingsResponse;
import com.tanmaysinghx.portalsso.analytics.retention.web.dto.UpdateRetentionSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin controls for scheduled deletion of sign-in and audit history. */
@RestController
@RequestMapping("/api/admin/analytics/retention")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRetentionSettingsController {

    private final AnalyticsRetentionSettingsService service;

    public AdminRetentionSettingsController(AnalyticsRetentionSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<RetentionSettingsResponse> getSettings() {
        return ResponseEntity.ok(service.currentSettings());
    }

    @PutMapping
    public ResponseEntity<RetentionSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateRetentionSettingsRequest request) {
        return ResponseEntity.ok(service.save(request));
    }
}
