package com.tanmaysinghx.portalsso.security;

import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

/** Remember-me tokens must not turn into an MFA bypass for accounts with MFA enabled. */
public class MfaAwareRememberMeServices extends TokenBasedRememberMeServices {

    private final UserRepository userRepository;

    public MfaAwareRememberMeServices(
            String key, UserDetailsService userDetailsService, UserRepository userRepository) {
        super(key, userDetailsService);
        this.userRepository = userRepository;
        setTokenValiditySeconds(30 * 24 * 60 * 60);
        setParameter("remember-me");
    }

    @Override
    protected UserDetails processAutoLoginCookie(
            String[] cookieTokens, HttpServletRequest request, HttpServletResponse response) {
        UserDetails userDetails = super.processAutoLoginCookie(cookieTokens, request, response);
        boolean mfaEnabled = userRepository.findByEmail(userDetails.getUsername())
                .map(user -> user.isMfaEnabled())
                .orElse(false);
        if (mfaEnabled) {
            throw new InvalidCookieException("Remember-me sign-in is disabled for MFA-enabled accounts");
        }
        return userDetails;
    }
}
