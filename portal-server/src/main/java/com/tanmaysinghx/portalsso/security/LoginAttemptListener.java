package com.tanmaysinghx.portalsso.security;

import com.tanmaysinghx.portalsso.analytics.service.LoginEventRecorder;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records login outcomes against the user row and locks an account after too many consecutive
 * failures.
 *
 * <p>The {@code last_login_at}, {@code failed_login_attempts} and {@code account_locked} columns
 * existed in the schema from the start but nothing ever wrote to them, which is why the admin
 * console showed "Never" for every user's last login. Enforcement was already in place —
 * {@link PortalUserDetailsService} maps {@code accountLocked} onto the {@code UserDetails}, so
 * Spring Security rejects a locked account on the next attempt — only the recording half was
 * missing.
 *
 * <p>Listening to authentication <em>events</em> rather than wiring a success/failure handler on
 * the form-login filter means this also covers the OAuth2 flows and remember-me, not just the
 * admin console's own login form.
 */
@Component
public class LoginAttemptListener {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptListener.class);

    private final UserRepository userRepository;
    private final LoginEventRecorder loginEventRecorder;
    private final int maxFailedAttempts;

    public LoginAttemptListener(
            UserRepository userRepository,
            LoginEventRecorder loginEventRecorder,
            @Value("${app.security.max-failed-login-attempts:5}") int maxFailedAttempts) {
        this.userRepository = userRepository;
        this.loginEventRecorder = loginEventRecorder;
        this.maxFailedAttempts = maxFailedAttempts;
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        userRepository.findByEmail(username).ifPresentOrElse(user -> {
            loginEventRecorder.record(username, true, user.getId().toString());
            user.setLastLoginAt(Instant.now());
            // A successful sign-in clears the streak, so occasional typos never accumulate into a
            // lockout over days.
            if (user.getFailedLoginAttempts() != 0) {
                user.setFailedLoginAttempts(0);
            }
            userRepository.save(user);
        },
        // A successful authentication with no matching row still belongs in the log — for example
        // an OAuth2 client authenticating rather than a person.
        () -> loginEventRecorder.record(username, true, null));
    }

    /**
     * Listens to the abstract failure event so every cause counts — bad password, disabled,
     * expired credentials — rather than only {@code BadCredentials}.
     */
    @EventListener
    @Transactional
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        if (username == null) {
            return;
        }

        // The attempt is always recorded, even for an address with no account — those are exactly
        // the attempts worth seeing in the dashboard. Only the per-user counter needs a row.
        userRepository.findByEmail(username).ifPresentOrElse(user -> {
            loginEventRecorder.record(username, false, user.getId().toString());
            if (user.isAccountLocked()) {
                return;
            }

            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= maxFailedAttempts) {
                user.setAccountLocked(true);
                log.warn("Locked account '{}' after {} consecutive failed login attempts", username, attempts);
            }

            userRepository.save(user);
        },
        () -> loginEventRecorder.record(username, false, null));
    }
}
