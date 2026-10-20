package com.tanmaysinghx.portalsso.security.signin;

import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Decides what, if anything, stands between a correct password and a session. One decision, read
 * by both the sign-in success handler and the remember-me service, so they cannot disagree about
 * whether a sign-in is finished.
 */
@Component
public class SecondStep {

    public enum Kind {
        NONE,
        /** Checked first: the password just typed is the one being retired. */
        PASSWORD_EXPIRED,
        MFA
    }

    private final UserRepository userRepository;
    private final PasswordPolicy passwordPolicy;

    public SecondStep(UserRepository userRepository, PasswordPolicy passwordPolicy) {
        this.userRepository = userRepository;
        this.passwordPolicy = passwordPolicy;
    }

    public Kind after(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return Kind.NONE;
        }
        if (passwordPolicy.isExpired(user.getPasswordChangedAt(), Instant.now())) {
            return Kind.PASSWORD_EXPIRED;
        }
        return user.isMfaEnabled() ? Kind.MFA : Kind.NONE;
    }
}
