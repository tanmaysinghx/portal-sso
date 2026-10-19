package com.tanmaysinghx.portalsso.security.magiclink.web;

import com.tanmaysinghx.portalsso.common.error.PortalException;
import com.tanmaysinghx.portalsso.mail.service.EmailSettingsService;
import com.tanmaysinghx.portalsso.security.PortalUserDetailsService;
import com.tanmaysinghx.portalsso.security.magiclink.service.MagicLinkService;
import com.tanmaysinghx.portalsso.security.mfa.MfaAuthenticationSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Where a magic link lands. Redeems the token and establishes the session.
 *
 * <p>Mirrors the MFA challenge in {@code AuthPageController} on purpose — same
 * {@code SecurityContextRepository}, same {@code AuthenticationSuccessEvent} — so a passwordless
 * sign-in is indistinguishable from any other once it has happened. In particular, publishing the
 * event is what keeps {@code LoginAttemptListener} in the loop, so the sign-in lands in
 * {@code login_events}, resets the failure counter, updates {@code last_login_at} and runs the
 * new-device check exactly as a password sign-in does. Skipping it would create a sign-in route
 * that is invisible to every one of those.
 */
@Controller
public class MagicLinkPageController {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkPageController.class);

    private final MagicLinkService magicLinkService;
    private final PortalUserDetailsService userDetailsService;
    private final EmailSettingsService emailSettingsService;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public MagicLinkPageController(
            MagicLinkService magicLinkService,
            PortalUserDetailsService userDetailsService,
            EmailSettingsService emailSettingsService,
            ApplicationEventPublisher eventPublisher) {
        this.magicLinkService = magicLinkService;
        this.userDetailsService = userDetailsService;
        this.emailSettingsService = emailSettingsService;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping("/magic-link")
    public String consume(
            @RequestParam(name = "token", required = false) String token,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        MagicLinkService.Redemption redemption;
        try {
            redemption = magicLinkService.redeem(token);
        } catch (PortalException e) {
            // Rendered rather than redirected so the reason survives. "Expired" and "already used"
            // need different advice, and a bare redirect to /login?error loses both.
            model.addAttribute("productName", emailSettingsService.currentBranding().productName());
            model.addAttribute("errorCode", e.getErrorCode().getCode());
            model.addAttribute("message", e.getMessage());
            return "magic-link-error";
        }

        if (redemption.mfaRequired()) {
            // A second factor exists so that one compromised channel is not enough, and email is a
            // channel. The link proves the address; it does not stand in for the factor.
            HttpSession session = request.getSession(true);
            session.setAttribute(MfaAuthenticationSuccessHandler.MFA_PRE_AUTH_EMAIL_ATTR, redemption.email());
            SecurityContextHolder.clearContext();
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            return "redirect:/mfa-challenge";
        }

        // Rotate the session id before granting any authority to it. Unlike the MFA path — which
        // runs after formLogin has already rotated — nothing has authenticated in this session yet,
        // so the id may be one an attacker planted in the victim's browser before sending them a
        // link they solicited. Rotating here is what stops that fixated session inheriting the
        // sign-in.
        request.changeSessionId();

        UserDetails userDetails = userDetailsService.loadUserByUsername(redemption.email());
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        eventPublisher.publishEvent(new AuthenticationSuccessEvent(authentication));
        log.info("Magic link sign-in completed for '{}' (accountCreated={})",
                redemption.email(), redemption.accountCreated());

        // Honour an interrupted OAuth2 authorization the same way the password flow does: a user
        // who started at a relying application should end up back there, not on the portal home.
        SavedRequest saved = new HttpSessionRequestCache().getRequest(request, response);
        if (saved != null) {
            return "redirect:" + saved.getRedirectUrl();
        }
        return "redirect:/";
    }
}
