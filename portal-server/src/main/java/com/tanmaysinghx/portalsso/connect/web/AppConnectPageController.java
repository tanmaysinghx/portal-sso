package com.tanmaysinghx.portalsso.connect.web;

import com.tanmaysinghx.portalsso.common.error.PortalException;
import com.tanmaysinghx.portalsso.connect.service.AppConnectService;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The approval screen for "Connect with Portal SSO". Requires a signed-in session (see
 * {@code SecurityConfig}); only administrators can approve or deny. Anyone else sees who to ask.
 *
 * <p>Every decision is re-validated from the stored request: the form carries nothing but the
 * administrator's choice about the external auth API, so a tampered form cannot widen what the
 * app receives or change where the code is sent.
 */
@Controller
public class AppConnectPageController {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final AppConnectService connectService;

    public AppConnectPageController(AppConnectService connectService) {
        this.connectService = connectService;
    }

    @GetMapping("/connect/{id}")
    public String review(@PathVariable UUID id, Authentication authentication, Model model) {
        try {
            AppConnectService.Review review = connectService.review(id);
            model.addAttribute("review", review);
            model.addAttribute("expiresText", EXPIRY_FORMAT.format(review.request().getExpiresAt()));
        } catch (PortalException e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("principalName", authentication.getName());
        model.addAttribute("isAdmin", isAdmin(authentication));
        return "connect";
    }

    @PostMapping("/connect/{id}/approve")
    public String approve(
            @PathVariable UUID id,
            @RequestParam(name = "allowExternalAuth", defaultValue = "false") boolean allowExternalAuth,
            @RequestParam(name = "allowExternalRegistration", defaultValue = "false") boolean allowExternalRegistration,
            Authentication authentication,
            Model model) {
        if (!isAdmin(authentication)) {
            return review(id, authentication, model);
        }
        try {
            return "redirect:" + connectService.approve(id, allowExternalAuth, allowExternalRegistration, authentication.getName());
        } catch (PortalException e) {
            return reviewWithError(id, authentication, model, e);
        }
    }

    @PostMapping("/connect/{id}/deny")
    public String deny(@PathVariable UUID id, Authentication authentication, Model model) {
        if (!isAdmin(authentication)) {
            return review(id, authentication, model);
        }
        try {
            return "redirect:" + connectService.deny(id, authentication.getName());
        } catch (PortalException e) {
            return reviewWithError(id, authentication, model, e);
        }
    }

    /** Re-sends an approved app's delivery code; it only ever goes to the registered callback. */
    @PostMapping("/connect/{id}/resume")
    public String resume(@PathVariable UUID id, Authentication authentication, Model model) {
        try {
            return "redirect:" + connectService.resume(id);
        } catch (PortalException e) {
            return reviewWithError(id, authentication, model, e);
        }
    }

    private String reviewWithError(UUID id, Authentication authentication, Model model, PortalException e) {
        String view = review(id, authentication, model);
        model.addAttribute("actionError", e.getMessage());
        return view;
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
