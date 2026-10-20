package com.tanmaysinghx.portalsso.security.web;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.security.mfa.MfaAuthenticationSuccessHandler;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy.PasswordPolicyDescription;
import com.tanmaysinghx.portalsso.security.signin.SignInCompletion;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The forced change after signing in with an expired password.
 *
 * <p>Reached only from {@link MfaAuthenticationSuccessHandler}, which has just verified the old
 * password, so it is not asked for again. The session holds the address but no authority until the
 * new password is saved; after that the sign-in carries on to the MFA challenge if the account has
 * one, or completes.
 */
@Controller
public class PasswordExpiredPageController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SignInCompletion signInCompletion;
    private final OAuthClientRepository clientRepository;
    private final AuditService auditService;

    public PasswordExpiredPageController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            SignInCompletion signInCompletion,
            OAuthClientRepository clientRepository,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.signInCompletion = signInCompletion;
        this.clientRepository = clientRepository;
        this.auditService = auditService;
    }

    @GetMapping("/password-expired")
    public String page(HttpServletRequest request, HttpServletResponse response, Model model) {
        String email = pendingEmail(request);
        if (email == null) {
            return "redirect:/login";
        }
        populate(model, request, response, email, null);
        return "password-expired";
    }

    @PostMapping("/password-expired")
    @Transactional
    public String change(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {
        String email = pendingEmail(request);
        if (email == null) {
            return "redirect:/login";
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.isEnabled() || user.isAccountLocked()) {
            return "redirect:/login?error";
        }

        String problem = null;
        if (!newPassword.equals(confirmPassword)) {
            problem = "The two passwords don't match.";
        } else if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            problem = "Choose a password different from the one that expired.";
        } else {
            try {
                passwordPolicy.validate(newPassword);
            } catch (BusinessRuleViolationException e) {
                problem = e.getMessage();
            }
        }
        if (problem != null) {
            populate(model, request, response, email, problem);
            return "password-expired";
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        auditService.record(AuditAction.USER_PASSWORD_CHANGED, user.getId(), email, "reason=expired");

        HttpSession session = request.getSession();
        session.removeAttribute(MfaAuthenticationSuccessHandler.PASSWORD_EXPIRED_EMAIL_ATTR);

        if (user.isMfaEnabled()) {
            session.setAttribute(MfaAuthenticationSuccessHandler.MFA_PRE_AUTH_EMAIL_ATTR, email);
            return "redirect:/mfa-challenge";
        }
        // The sign-in event was already published when the password was checked at /login, so it is
        // not published again here — that would count one sign-in twice in login_events.
        return "redirect:" + signInCompletion.complete(request, response, email);
    }

    private void populate(
            Model model, HttpServletRequest request, HttpServletResponse response, String email, String errorMessage) {
        PasswordPolicyDescription policy = passwordPolicy.describe();
        model.addAttribute("email", email);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("maxAgeDays", policy.maxAgeDays());
        model.addAttribute("maxLength", policy.maxLength());
        model.addAttribute("rules", rules(policy));
        resolveClient(request, response).ifPresent(client -> {
            model.addAttribute("clientName", client.getClientName());
            model.addAttribute("clientLogoUrl", client.getLogoUrl());
        });
    }

    private static List<String> rules(PasswordPolicyDescription policy) {
        List<String> rules = new ArrayList<>();
        rules.add("At least " + policy.minLength() + " characters");
        if (policy.requireUppercase()) rules.add("An upper-case letter");
        if (policy.requireLowercase()) rules.add("A lower-case letter");
        if (policy.requireDigit()) rules.add("A number");
        if (policy.requireSymbol()) rules.add("A symbol");
        return rules;
    }

    private static String pendingEmail(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(MfaAuthenticationSuccessHandler.PASSWORD_EXPIRED_EMAIL_ATTR);
    }

    private Optional<OAuthClient> resolveClient(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = new HttpSessionRequestCache().getRequest(request, response);
        String[] values = saved == null ? null : saved.getParameterValues("client_id");
        return values == null || values.length == 0 ? Optional.empty() : clientRepository.findByClientId(values[0]);
    }
}
