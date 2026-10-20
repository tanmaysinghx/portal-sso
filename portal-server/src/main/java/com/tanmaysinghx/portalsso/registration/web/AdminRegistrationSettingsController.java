package com.tanmaysinghx.portalsso.registration.web;

import com.tanmaysinghx.portalsso.registration.service.RegistrationSettingsService;
import com.tanmaysinghx.portalsso.registration.web.dto.RegistrationSettingsResponse;
import com.tanmaysinghx.portalsso.registration.web.dto.UpdateRegistrationSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/registration")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRegistrationSettingsController {
    private final RegistrationSettingsService service;

    public AdminRegistrationSettingsController(RegistrationSettingsService service) { this.service = service; }

    @GetMapping
    public RegistrationSettingsResponse getSettings() { return service.currentSettings(); }

    @PutMapping
    public RegistrationSettingsResponse updateSettings(@Valid @RequestBody UpdateRegistrationSettingsRequest request) {
        return service.save(request);
    }
}
