package com.tanmaysinghx.portalsso.security.signin;

import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
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
 *
 * <p>For MFA accounts the signature also covers the enrolled MFA secret, so a cookie stops working
 * the moment MFA is enabled, reset or re-enrolled, rather than skipping the new second factor.
 */
public class PortalRememberMeServices extends TokenBasedRememberMeServices {

    /** Session flag: the sign-in form asked to be remembered, and a second step is still pending. */
    static final String PENDING_ATTR = "SIGN_IN_REMEMBER_ME_PENDING";

    private final SecondStep secondStep;
    private final UserRepository userRepository;

    public PortalRememberMeServices(
            String key, UserDetailsService userDetailsService, SecondStep secondStep, UserRepository userRepository) {
        super(key, userDetailsService);
        this.secondStep = secondStep;
        this.userRepository = userRepository;
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

    @Override
    protected String makeTokenSignature(
            long tokenExpiryTime, String username, String password, RememberMeTokenAlgorithm algorithm) {
        String baseSignature = super.makeTokenSignature(tokenExpiryTime, username, password, algorithm);
        var user = userRepository.findByEmail(username).orElse(null);
        if (user == null || !user.isMfaEnabled()) {
            // Non-MFA cookies keep the stock signature. Enabling MFA later changes it, so an old
            // cookie can no longer skip the new second factor.
            return baseSignature;
        }
        String mfaState = user.getMfaSecret() == null ? "" : user.getMfaSecret();
        try {
            byte[] stateHash = MessageDigest.getInstance("SHA-256")
                    .digest((baseSignature + ":" + mfaState).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(stateHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
