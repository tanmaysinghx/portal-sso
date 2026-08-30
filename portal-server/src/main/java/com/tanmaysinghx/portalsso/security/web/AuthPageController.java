package com.tanmaysinghx.portalsso.security.web;

import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.security.PortalUserDetailsService;
import com.tanmaysinghx.portalsso.security.mfa.MfaAuthenticationSuccessHandler;
import com.tanmaysinghx.portalsso.security.mfa.MfaService;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the server-rendered screens an <em>end user</em> meets during an authentication flow:
 * sign-in, MFA challenge, and OAuth2 consent.
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
    private final MfaService mfaService;
    private final UserRepository userRepository;
    private final PortalUserDetailsService userDetailsService;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthPageController(
            OAuthClientRepository clientRepository,
            OAuth2AuthorizationConsentService consentService,
            MfaService mfaService,
            UserRepository userRepository,
            PortalUserDetailsService userDetailsService,
            ApplicationEventPublisher eventPublisher) {
        this.clientRepository = clientRepository;
        this.consentService = consentService;
        this.mfaService = mfaService;
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.eventPublisher = eventPublisher;
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

        resolveClientFromSavedRequest(request, response)
                .ifPresent(client -> {
                    model.addAttribute("clientName", client.getClientName());
                    model.addAttribute("clientLogoUrl", client.getLogoUrl());
                });

        return "login";
    }

    @GetMapping("/mfa-challenge")
    public String mfaChallenge(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(required = false) String error,
            Model model) {

        HttpSession session = request.getSession(false);
        String email = session != null
                ? (String) session.getAttribute(MfaAuthenticationSuccessHandler.MFA_PRE_AUTH_EMAIL_ATTR)
                : null;

        if (email == null) {
            return "redirect:/login";
        }

        model.addAttribute("email", email);
        model.addAttribute("error", error != null);

        resolveClientFromSavedRequest(request, response)
                .ifPresent(client -> {
                    model.addAttribute("clientName", client.getClientName());
                    model.addAttribute("clientLogoUrl", client.getLogoUrl());
                });

        return "mfa-challenge";
    }

    @PostMapping("/mfa-challenge")
    public String verifyMfaChallenge(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam("code") String code) {

        HttpSession session = request.getSession(false);
        String email = session != null
                ? (String) session.getAttribute(MfaAuthenticationSuccessHandler.MFA_PRE_AUTH_EMAIL_ATTR)
                : null;

        if (email == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.isEnabled() || user.isAccountLocked()) {
            return "redirect:/login?error";
        }

        boolean verified = mfaService.verifyMfaOrRecoveryCode(user, code);
        if (!verified) {
            // Count failed attempt toward lockout & record failure event
            eventPublisher.publishEvent(new AuthenticationFailureBadCredentialsEvent(
                    new UsernamePasswordAuthenticationToken(email, ""),
                    new BadCredentialsException("Invalid MFA verification code")));
            return "redirect:/mfa-challenge?error";
        }

        // MFA Verified: upgrade session to full authenticated state
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        session.removeAttribute(MfaAuthenticationSuccessHandler.MFA_PRE_AUTH_EMAIL_ATTR);
        eventPublisher.publishEvent(new AuthenticationSuccessEvent(authentication));

        // Follow saved request (e.g. from /oauth2/authorize) or land on root
        SavedRequest saved = new HttpSessionRequestCache().getRequest(request, response);
        if (saved != null) {
            return "redirect:" + saved.getRedirectUrl();
        }

        return "redirect:/";
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
