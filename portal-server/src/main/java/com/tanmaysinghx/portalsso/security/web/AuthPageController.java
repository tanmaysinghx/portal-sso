package com.tanmaysinghx.portalsso.security.web;

import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the two screens an <em>end user</em> meets during an OAuth2 flow: sign-in and consent.
 *
 * <p>These are server-rendered rather than part of the Angular console because the browser is
 * mid-redirect between the relying application and this server when they appear — there is no SPA
 * loaded at that point. They replace Spring Security's built-in login page, which is unstyled and
 * says nothing about which application is asking.
 */
@Controller
public class AuthPageController {

    /** Plain-English descriptions; anything unrecognised falls back to the raw scope name. */
    private static final java.util.Map<String, String> SCOPE_DESCRIPTIONS = java.util.Map.of(
            "openid", "Verify who you are",
            "profile", "See your name",
            "email", "See your email address");

    private final OAuthClientRepository clientRepository;
    private final OAuth2AuthorizationConsentService consentService;

    public AuthPageController(
            OAuthClientRepository clientRepository, OAuth2AuthorizationConsentService consentService) {
        this.clientRepository = clientRepository;
        this.consentService = consentService;
    }

    @GetMapping("/login")
    public String login(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            Model model) {

        model.addAttribute("error", error != null);
        model.addAttribute("loggedOut", logout != null);

        // When the user arrived via /oauth2/authorize, Spring Security stashed that request before
        // redirecting here. Reading client_id back out is what lets the page say which application
        // is asking, instead of a generic prompt.
        resolveClientFromSavedRequest(request, response)
                .ifPresent(client -> {
                    model.addAttribute("clientName", client.getClientName());
                    model.addAttribute("clientLogoUrl", client.getLogoUrl());
                });

        return "login";
    }

    @GetMapping("/oauth2/consent")
    public String consent(
            Authentication authentication,
            @RequestParam("client_id") String clientId,
            @RequestParam("scope") String scope,
            @RequestParam("state") String state,
            @RequestParam(name = "user_code", required = false) String userCode,
            Model model) {

        OAuthClient client = clientRepository.findByClientId(clientId).orElse(null);

        Set<String> alreadyApproved = new LinkedHashSet<>();
        OAuth2AuthorizationConsent existing =
                client == null ? null : consentService.findById(client.getId().toString(), authentication.getName());
        if (existing != null) {
            alreadyApproved.addAll(existing.getScopes());
        }

        // openid is implicit in an OIDC request and is not something a user can meaningfully decline,
        // so it is never offered as a checkbox.
        List<ScopeView> requested = new ArrayList<>();
        for (String requestedScope : scope.split(" ")) {
            if (requestedScope.isBlank() || "openid".equals(requestedScope)) {
                continue;
            }
            requested.add(new ScopeView(
                    requestedScope,
                    SCOPE_DESCRIPTIONS.getOrDefault(requestedScope, requestedScope),
                    alreadyApproved.contains(requestedScope)));
        }

        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", client == null ? clientId : client.getClientName());
        model.addAttribute("clientLogoUrl", client == null ? null : client.getLogoUrl());
        model.addAttribute("state", state);
        model.addAttribute("userCode", userCode);
        model.addAttribute("scopes", requested);
        model.addAttribute("principalName", authentication.getName());

        return "consent";
    }

    private Optional<OAuthClient> resolveClientFromSavedRequest(
            HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = new HttpSessionRequestCache().getRequest(request, response);
        if (saved == null) {
            return Optional.empty();
        }
        String[] values = saved.getParameterValues("client_id");
        if (values == null || values.length == 0) {
            return Optional.empty();
        }
        return clientRepository.findByClientId(values[0]);
    }

    /** @param approved whether the user granted this scope on a previous visit. */
    public record ScopeView(String scope, String description, boolean approved) {}
}
