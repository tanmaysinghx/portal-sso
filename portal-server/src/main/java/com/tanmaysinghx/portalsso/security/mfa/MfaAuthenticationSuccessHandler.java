package com.tanmaysinghx.portalsso.security.mfa;

import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Intercepts successful username/password authentication on {@code /login}.
 *
 * <p>If the user has MFA enabled, their credentials are held in a pre-authenticated session state
 * and they are redirected to {@code /mfa-challenge}. The full {@code SecurityContext} is not
 * populated until the second factor (TOTP or recovery code) is verified.
 */
@Component
public class MfaAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    public static final String MFA_PRE_AUTH_EMAIL_ATTR = "MFA_PRE_AUTH_EMAIL";

    private final UserRepository userRepository;
    private final SavedRequestAwareAuthenticationSuccessHandler defaultSuccessHandler =
            new SavedRequestAwareAuthenticationSuccessHandler();

    public MfaAuthenticationSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        String email = authentication.getName();
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent() && userOpt.get().isMfaEnabled()) {
            // User requires MFA challenge. Prevent full authentication in session.
            HttpSession session = request.getSession(true);
            session.setAttribute(MFA_PRE_AUTH_EMAIL_ATTR, email);

            // Clear the SecurityContext so endpoints under /api/** remain 401 until MFA completes
            SecurityContextHolder.clearContext();
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);

            response.sendRedirect("/mfa-challenge");
            return;
        }

        // Standard user without MFA: proceed directly to saved request or default landing page
        defaultSuccessHandler.onAuthenticationSuccess(request, response, authentication);
    }
}
