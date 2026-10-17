package com.tanmaysinghx.portalsso.application.web;

import com.tanmaysinghx.portalsso.application.service.ApplicationOnboardingService;
import com.tanmaysinghx.portalsso.application.service.ApplicationService;
import com.tanmaysinghx.portalsso.application.service.ExternalApplicationAuthService;
import com.tanmaysinghx.portalsso.application.web.dto.ApplicationApiKeyResponse;
import com.tanmaysinghx.portalsso.application.web.dto.ApplicationCredentialsResponse;
import com.tanmaysinghx.portalsso.application.web.dto.ApplicationOnboardingResponse;
import com.tanmaysinghx.portalsso.application.web.dto.ApplicationResponse;
import com.tanmaysinghx.portalsso.application.web.dto.CreateApplicationRequest;
import com.tanmaysinghx.portalsso.application.web.dto.OnboardApplicationRequest;
import com.tanmaysinghx.portalsso.application.web.dto.UpdateApplicationRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/applications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationOnboardingService onboardingService;

    public AdminApplicationController(ApplicationService applicationService, ApplicationOnboardingService onboardingService) {
        this.applicationService = applicationService;
        this.onboardingService = onboardingService;
    }

    @GetMapping
    public List<ApplicationResponse> list(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "enabled", required = false) Boolean enabled) {
        return applicationService.listAdmin(search, category, enabled);
    }

    @GetMapping("/{id}")
    public ApplicationResponse getById(@PathVariable UUID id) {
        return applicationService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@Valid @RequestBody CreateApplicationRequest request) {
        return applicationService.create(request);
    }

    /**
     * One-step onboarding: creates the application, optionally a new linked OAuth client, and the
     * external API key in one transaction, returning every credential in a single response. This is
     * the only time the client secret and API key are visible.
     */
    @PostMapping("/onboard")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationOnboardingResponse onboard(@Valid @RequestBody OnboardApplicationRequest request) {
        return onboardingService.onboard(request);
    }

    /** Issuer, client and external-API settings for an app. Secrets are never included. */
    @GetMapping("/{id}/credentials")
    public ApplicationCredentialsResponse credentials(@PathVariable UUID id) {
        return onboardingService.credentials(id);
    }

    /** New secret for the linked confidential client, shown once; the old one stops working. */
    @PostMapping("/{id}/credentials/client-secret")
    public ApplicationCredentialsResponse regenerateClientSecret(@PathVariable UUID id) {
        return onboardingService.regenerateClientSecret(id);
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateApplicationRequest request) {
        return applicationService.update(id, request);
    }

    @PostMapping("/{id}/external-auth/api-key")
    public ApplicationApiKeyResponse rotateExternalAuthApiKey(@PathVariable UUID id) {
        String apiKey = applicationService.rotateExternalAuthApiKey(id);
        String basePath = "/api/external/apps/" + id;
        return new ApplicationApiKeyResponse(
                apiKey,
                apiKey.substring(0, 12),
                ExternalApplicationAuthService.API_KEY_HEADER,
                basePath + "/register",
                basePath + "/login");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        applicationService.delete(id);
    }
}
