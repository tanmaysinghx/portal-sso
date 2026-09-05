package com.tanmaysinghx.portalsso.application.web;

import com.tanmaysinghx.portalsso.application.service.ApplicationService;
import com.tanmaysinghx.portalsso.application.web.dto.UserApplicationResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/applications")
@PreAuthorize("isAuthenticated()")
public class UserApplicationController {

    private final ApplicationService applicationService;

    public UserApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public List<UserApplicationResponse> getUserApplications(Authentication authentication) {
        return applicationService.getUserApplications(authentication);
    }
}
