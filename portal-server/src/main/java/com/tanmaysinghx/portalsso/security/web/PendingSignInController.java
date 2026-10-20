package com.tanmaysinghx.portalsso.security.web;

import com.tanmaysinghx.portalsso.security.mfa.MfaAuthenticationSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the console's sign-in page which step, if any, the caller's own sign-in is waiting on.
 *
 * <p>The console posts to {@code /login} with fetch, and a correct password followed by a pending
 * step looks exactly like a wrong one from there: /me says "not signed in" either way. Following the
 * redirect chain to find out is unreliable — behind a proxy the {@code Location} can name another
 * origin and the fetch dies on CORS — so the session is simply asked. It reveals nothing beyond the
 * caller's own session, which they just established.
 */
@RestController
public class PendingSignInController {

    @GetMapping("/api/public/sign-in/pending")
    public PendingSignInResponse pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(MfaAuthenticationSuccessHandler.PASSWORD_EXPIRED_EMAIL_ATTR) != null) {
            return new PendingSignInResponse("PASSWORD_EXPIRED", "/password-expired");
        }
        if (session != null && session.getAttribute(MfaAuthenticationSuccessHandler.MFA_PRE_AUTH_EMAIL_ATTR) != null) {
            return new PendingSignInResponse("MFA", "/mfa-challenge");
        }
        return new PendingSignInResponse(null, null);
    }

    /** {@code step} and {@code path} are both null when nothing is pending. */
    public record PendingSignInResponse(String step, String path) {}
}
