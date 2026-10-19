package com.tanmaysinghx.portalsso.security;

import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

/** Remember-me tokens for MFA accounts are issued only after the MFA challenge succeeds. */
public class MfaAwareRememberMeServices extends TokenBasedRememberMeServices {

    private final UserRepository userRepository;

    public MfaAwareRememberMeServices(
            String key, UserDetailsService userDetailsService, UserRepository userRepository) {
        super(key, userDetailsService);
        this.userRepository = userRepository;
        setTokenValiditySeconds(30 * 24 * 60 * 60);
        setParameter("remember-me");
    }

    /** Issues a persistent cookie after MFA; the original checkbox was submitted before the challenge. */
    public void loginSuccessAfterMfa(
            HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.Authentication authentication) {
        onLoginSuccess(request, response, authentication);
    }

    @Override
    protected String makeTokenSignature(
            long tokenExpiryTime, String username, String password, RememberMeTokenAlgorithm algorithm) {
        String baseSignature = super.makeTokenSignature(tokenExpiryTime, username, password, algorithm);
        var user = userRepository.findByEmail(username).orElse(null);
        if (user == null || !user.isMfaEnabled()) {
            // Keep existing non-MFA remember-me cookies valid. If MFA is enabled later, its
            // signature changes and the old cookie can no longer skip the new second factor.
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
