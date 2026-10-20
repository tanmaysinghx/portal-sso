package com.tanmaysinghx.portalsso.security.signin;

import com.tanmaysinghx.portalsso.security.PortalUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

/**
 * The two halves of a sign-in that has a step after the password: holding it back, then finishing it.
 *
 * <p>Used by the MFA challenge and the expired-password change, so both finish a sign-in the same
 * way: a new session id, the security context saved, and the remember-me cookie issued only now
 * (see {@link PortalRememberMeServices}).
 */
@Component
public class SignInCompletion {

    private final PortalRememberMeServices rememberMeServices;
    private final PortalUserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public SignInCompletion(PortalRememberMeServices rememberMeServices, PortalUserDetailsService userDetailsService) {
        this.rememberMeServices = rememberMeServices;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Holds the sign-in back: the password step has already put an authenticated context in the
     * session, and it must not survive until the second step is done.
     */
    public void defer(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        request.getSession(true).removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    }

    /**
     * Grants the full sign-in for {@code email}.
     *
     * @return where to send the browser: the saved request (an OAuth2 authorize call, say) or "/".
     */
    public String complete(HttpServletRequest request, HttpServletResponse response, String email) {
        // Rotated before authority is granted, so an id seen before the second step is useless after.
        request.changeSessionId();
        HttpSession session = request.getSession();

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        if (Boolean.TRUE.equals(session.getAttribute(PortalRememberMeServices.PENDING_ATTR))) {
            session.removeAttribute(PortalRememberMeServices.PENDING_ATTR);
            rememberMeServices.issueDeferred(request, response, authentication);
        }

        SavedRequest saved = new HttpSessionRequestCache().getRequest(request, response);
        return saved != null ? saved.getRedirectUrl() : "/";
    }
}
