package com.tanmaysinghx.portalsso.security.mfa;

import com.tanmaysinghx.portalsso.security.signin.SecondStep;
import com.tanmaysinghx.portalsso.security.signin.SignInCompletion;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Intercepts successful username/password authentication on {@code /login}.
 *
 * <p>Two things can stand between a correct password and a session — an expired password, then
 * MFA; {@link SecondStep} decides which. Either way the {@code SecurityContext} is not populated,
 * and no remember-me cookie is issued, until the step completes ({@link SignInCompletion}).
 */
@Component
public class MfaAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    public static final String MFA_PRE_AUTH_EMAIL_ATTR = "MFA_PRE_AUTH_EMAIL";
    public static final String PASSWORD_EXPIRED_EMAIL_ATTR = "PASSWORD_EXPIRED_EMAIL";

    private final SecondStep secondStep;
    private final SignInCompletion signInCompletion;
    private final SavedRequestAwareAuthenticationSuccessHandler defaultSuccessHandler =
            new SavedRequestAwareAuthenticationSuccessHandler();

    public MfaAuthenticationSuccessHandler(SecondStep secondStep, SignInCompletion signInCompletion) {
        this.secondStep = secondStep;
        this.signInCompletion = signInCompletion;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        String email = authentication.getName();
        switch (secondStep.after(email)) {
            case PASSWORD_EXPIRED -> {
                signInCompletion.defer(request);
                request.getSession().setAttribute(PASSWORD_EXPIRED_EMAIL_ATTR, email);
                response.sendRedirect("/password-expired");
            }
            case MFA -> {
                signInCompletion.defer(request);
                request.getSession().setAttribute(MFA_PRE_AUTH_EMAIL_ATTR, email);
                response.sendRedirect("/mfa-challenge");
            }
            case NONE -> defaultSuccessHandler.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
