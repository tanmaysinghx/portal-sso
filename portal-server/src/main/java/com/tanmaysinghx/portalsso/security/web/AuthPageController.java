package com.tanmaysinghx.portalsso.security.web;

import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.security.PortalUserDetailsService;
import com.tanmaysinghx.portalsso.common.web.ClientIpResolver;
import com.tanmaysinghx.portalsso.security.magiclink.service.MagicLinkService;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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
    private final MagicLinkService magicLinkService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthPageController(
            OAuthClientRepository clientRepository,
            OAuth2AuthorizationConsentService consentService,
            MfaService mfaService,
            UserRepository userRepository,
            PortalUserDetailsService userDetailsService,
            ApplicationEventPublisher eventPublisher,
            MagicLinkService magicLinkService) {
        this.clientRepository = clientRepository;
        this.consentService = consentService;
        this.mfaService = mfaService;
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.eventPublisher = eventPublisher;
        this.magicLinkService = magicLinkService;
    }

    @GetMapping("/login")
    public String login(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(name = "link", required = false) String link,
            Model model) {

        // Auto-login: If user is already authenticated and not logging out / erroring, resume flow immediately
        if (error == null && logout == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                SavedRequest saved = new HttpSessionRequestCache().getRequest(request, response);
                if (saved != null) {
                    return "redirect:" + saved.getRedirectUrl();
                }
                String returnUrl = request.getParameter("return_url");
                if (returnUrl == null || returnUrl.isBlank()) {
                    returnUrl = request.getParameter("returnUrl");
                }
                if (returnUrl == null || returnUrl.isBlank()) {
                    returnUrl = request.getParameter("redirect_uri");
                }
                if (returnUrl != null && !returnUrl.isBlank()) {
                    return "redirect:" + returnUrl;
                }
                return "redirect:/";
            }
        }

        model.addAttribute("error", error != null);
        model.addAttribute("loggedOut", logout != null);

        // Offered only when an administrator has turned it on, so the sign-in page does not
        // advertise a route that answers every request with "not enabled".
        model.addAttribute("magicLinkEnabled", magicLinkService.isEnabled());
        model.addAttribute("magicLinkSent", "sent".equals(link));
        model.addAttribute("magicLinkFailed", "error".equals(link));

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

    /**
     * Requests a magic link from the server-rendered sign-in page.
     *
     * <p>A form POST rather than the JSON endpoint, because this page is reached mid-redirect
     * during an OAuth2 flow and must work without JavaScript. Thymeleaf adds the CSRF field to any
     * {@code th:action} form, so this is protected exactly as the password form is.
     *
     * <p>Redirects back with a flag instead of rendering, so the outcome survives a refresh and the
     * saved OAuth2 request in the session is left untouched — the user is about to resume it from
     * their mailbox.
     */
    @PostMapping("/login/magic-link")
    public String requestMagicLink(
            @RequestParam("email") String email, HttpServletRequest request) {
        try {
            magicLinkService.requestLink(
                    email, ClientIpResolver.getClientIp(request), request.getHeader("User-Agent"));
            // "sent" whatever actually happened — the service does not reveal whether the address
            // has an account, so neither can this page.
            return "redirect:/login?link=sent";
        } catch (RuntimeException e) {
            // Only reached when the feature is off or no relay is configured: both describe the
            // server rather than the address, so showing a failure here leaks nothing.
            return "redirect:/login?link=error";
        }
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

    /**
     * Popup callback relay for the SDK's {@code loginWithPopup()}.
     *
     * <p>The code is posted to the opener immediately, so the app's token exchange starts at once
     * and sign-in is not slowed down. The window then stays open for at least {@code MIN_VISIBLE_MS},
     * walking through the handshake steps, so the user sees what happened instead of a flash. The
     * SDK stops watching the popup once it has the code, so closing it later is safe. Errors stay on
     * screen with a Close button rather than vanishing.
     */
    @GetMapping(value = "/oauth2/popup-callback", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String popupCallback() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="referrer" content="no-referrer">
              <title>Signing you in…</title>
              <link rel="icon" type="image/svg+xml" href="/logo.svg">
              <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                  min-height: 100vh; display: flex; align-items: center; justify-content: center;
                  background: radial-gradient(circle at 50% 0%, #1e293b 0%, #0b1120 70%);
                  color: #e2e8f0; padding: 24px;
                }
                .card { width: 100%; max-width: 340px; text-align: center; }
                .handshake { position: relative; height: 64px; display: flex; align-items: center; justify-content: center; gap: 56px; margin-bottom: 22px; }
                .node { width: 52px; height: 52px; border-radius: 14px; background: #111827; border: 1px solid #334155;
                  display: flex; align-items: center; justify-content: center; z-index: 1; }
                .node img { width: 30px; height: 30px; }
                .node svg { width: 26px; height: 26px; color: #a5b4fc; }
                .wire { position: absolute; left: 50%; top: 50%; width: 56px; height: 2px; transform: translate(-50%, -50%);
                  background: #334155; overflow: hidden; border-radius: 2px; }
                .wire::after { content: ""; position: absolute; top: 0; left: -40%; width: 40%; height: 100%;
                  background: linear-gradient(90deg, transparent, #818cf8, transparent); animation: pulse 1.1s ease-in-out infinite; }
                .done .wire { background: #22c55e; }
                .done .wire::after { display: none; }
                @keyframes pulse { to { left: 100%; } }
                h1 { font-size: 17px; font-weight: 600; color: #f8fafc; margin-bottom: 4px; }
                .sub { font-size: 13px; color: #94a3b8; margin-bottom: 20px; min-height: 18px; }
                ol { list-style: none; text-align: left; background: rgba(15, 23, 42, 0.7); border: 1px solid #1e293b;
                  border-radius: 12px; padding: 6px 14px; }
                li { display: flex; align-items: center; gap: 10px; padding: 9px 0; font-size: 13px; color: #64748b; transition: color .25s; }
                li + li { border-top: 1px solid #1e293b; }
                .dot { flex-shrink: 0; width: 18px; height: 18px; border-radius: 999px; border: 2px solid #334155;
                  display: flex; align-items: center; justify-content: center; }
                li.active { color: #e2e8f0; }
                li.active .dot { border-color: #818cf8; border-top-color: transparent; animation: spin .7s linear infinite; }
                li.ok { color: #cbd5e1; }
                li.ok .dot { border-color: #22c55e; background: #22c55e; }
                li.ok .dot::after { content: ""; width: 5px; height: 9px; border: solid #0b1120; border-width: 0 2px 2px 0;
                  transform: rotate(45deg) translate(-1px, -1px); }
                @keyframes spin { to { transform: rotate(360deg); } }
                .error { display: none; margin-top: 4px; padding: 12px 14px; border-radius: 10px; background: #450a0a;
                  border: 1px solid #7f1d1d; color: #fecaca; font-size: 13px; text-align: left; }
                .failed .error { display: block; }
                .failed ol, .failed .wire { display: none; }
                button { margin-top: 16px; width: 100%; height: 38px; border-radius: 8px; border: 1px solid #334155;
                  background: #111827; color: #e2e8f0; font-size: 13px; font-weight: 600; cursor: pointer; }
                .hint { margin-top: 14px; font-size: 11.5px; color: #475569; }
                @media (prefers-reduced-motion: reduce) { * { animation: none !important; transition: none !important; } }
              </style>
            </head>
            <body>
              <main class="card" id="card">
                <div class="handshake" aria-hidden="true">
                  <div class="node"><img src="/logo.svg" alt=""></div>
                  <div class="wire"></div>
                  <div class="node"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M9 17.25v1.007a3 3 0 01-.879 2.122L7.5 21h9l-.621-.621A3 3 0 0115 18.257V17.25m6-12V15a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 15V5.25m18 0A2.25 2.25 0 0018.75 3H5.25A2.25 2.25 0 003 5.25m18 0V12a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 12V5.25"/></svg></div>
                </div>
                <h1 id="title">Signing you in</h1>
                <p class="sub" id="sub" role="status" aria-live="polite">Contacting Portal SSO…</p>
                <ol id="steps">
                  <li><span class="dot"></span>Identity verified by Portal SSO</li>
                  <li><span class="dot"></span>Authorization code issued (PKCE&nbsp;S256)</li>
                  <li><span class="dot"></span>Handing the code to the app securely</li>
                  <li><span class="dot"></span>Establishing your session</li>
                </ol>
                <div class="error" id="error"></div>
                <button type="button" id="close" hidden>Close window</button>
                <p class="hint" id="hint">This window closes automatically.</p>
              </main>
              <script>
                (function () {
                  var MIN_VISIBLE_MS = 4000;
                  var params = new URLSearchParams(window.location.search);
                  var error = params.get('error');
                  var steps = document.querySelectorAll('#steps li');
                  var sub = document.getElementById('sub');
                  var title = document.getElementById('title');
                  var card = document.getElementById('card');
                  var closeBtn = document.getElementById('close');
                  closeBtn.addEventListener('click', function () { window.close(); });

                  // Post first, animate second: the app starts exchanging the code now, not after the animation.
                  var delivered = false;
                  try {
                    if (window.opener) {
                      window.opener.postMessage({
                        type: 'PORTAL_SSO_POPUP_CALLBACK',
                        code: params.get('code'),
                        state: params.get('state'),
                        error: error,
                        errorDescription: params.get('error_description')
                      }, '*');
                      delivered = true;
                    }
                  } catch (e) {
                    console.error('Popup callback error:', e);
                  }

                  if (error) {
                    card.classList.add('failed');
                    title.textContent = 'Sign-in did not complete';
                    sub.textContent = '';
                    document.getElementById('error').textContent = params.get('error_description') || error;
                    document.getElementById('hint').hidden = true;
                    closeBtn.hidden = false;
                    return;
                  }
                  if (!delivered) {
                    title.textContent = 'Signed in';
                    sub.textContent = 'The app window is no longer open. You can close this one.';
                    document.getElementById('steps').hidden = true;
                    document.getElementById('hint').hidden = true;
                    closeBtn.hidden = false;
                    return;
                  }

                  var labels = ['Contacting Portal SSO…', 'Verifying the handshake…', 'Securing the connection…', 'Starting your session…'];
                  var stepMs = MIN_VISIBLE_MS / (steps.length + 0.5);
                  Array.prototype.forEach.call(steps, function (li, i) {
                    setTimeout(function () {
                      if (i > 0) { steps[i - 1].className = 'ok'; }
                      li.className = 'active';
                      sub.textContent = labels[i];
                    }, i * stepMs);
                  });
                  setTimeout(function () {
                    steps[steps.length - 1].className = 'ok';
                    card.classList.add('done');
                    title.textContent = 'Signed in';
                    sub.textContent = 'Returning to the app…';
                  }, steps.length * stepMs);
                  setTimeout(function () { window.close(); }, MIN_VISIBLE_MS + 400);
                })();
              </script>
            </body>
            </html>
            """;
    }

    /** @param approved whether the user granted this scope on a previous visit. */
    public record ScopeView(String scope, String description, boolean approved) {}
}

