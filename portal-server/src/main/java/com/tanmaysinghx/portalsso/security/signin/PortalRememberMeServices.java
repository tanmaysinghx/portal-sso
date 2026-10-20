package com.tanmaysinghx.portalsso.security.signin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

/**
 * Remember-me that will not mint a cookie for a sign-in that is not finished.
 *
 * <p>Spring issues the cookie from the password step, before the success handler has a chance to
 * divert to MFA or a forced password change. Left alone, that cookie is a complete session that
 * never saw the second step. So here the cookie is only noted as wanted, and
 * {@link SignInCompletion#complete} issues it once the step is done.
 */
public class PortalRememberMeServices extends TokenBasedRememberMeServices {

    /** Session flag: the sign-in form asked to be remembered, and a second step is still pending. */
    static final String PENDING_ATTR = "SIGN_IN_REMEMBER_ME_PENDING";

    private final SecondStep secondStep;

    public PortalRememberMeServices(String key, UserDetailsService userDetailsService, SecondStep secondStep) {
        super(key, userDetailsService);
        this.secondStep = secondStep;
    }

    /** Only reached when the request asked to be remembered. */
    @Override
    public void onLoginSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication successfulAuthentication) {
        if (secondStep.after(successfulAuthentication.getName()) != SecondStep.Kind.NONE) {
            request.getSession(true).setAttribute(PENDING_ATTR, Boolean.TRUE);
            return;
        }
        super.onLoginSuccess(request, response, successfulAuthentication);
    }

    /** Issues the cookie a deferred sign-in asked for, now that its second step is complete. */
    void issueDeferred(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        super.onLoginSuccess(request, response, authentication);
    }
}
