package com.tanmaysinghx.portalsso.application.web;

import com.tanmaysinghx.portalsso.application.service.ExternalApplicationAuthService;
import com.tanmaysinghx.portalsso.application.web.dto.ExternalAppAuthResponse;
import com.tanmaysinghx.portalsso.application.web.dto.ExternalAppLoginRequest;
import com.tanmaysinghx.portalsso.registration.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/external/apps/{applicationId}")
public class ExternalApplicationAuthController {

    private final ExternalApplicationAuthService authService;

    public ExternalApplicationAuthController(ExternalApplicationAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ExternalAppAuthResponse register(
            @PathVariable UUID applicationId,
            @RequestHeader(ExternalApplicationAuthService.API_KEY_HEADER) String apiKey,
            @Valid @RequestBody RegisterRequest request) {
        return authService.register(applicationId, apiKey, request);
    }

    @PostMapping("/login")
    public ExternalAppAuthResponse login(
            @PathVariable UUID applicationId,
            @RequestHeader(ExternalApplicationAuthService.API_KEY_HEADER) String apiKey,
            @Valid @RequestBody ExternalAppLoginRequest request) {
        return authService.login(applicationId, apiKey, request);
    }
}
