package com.tanmaysinghx.portalsso.security;

import com.tanmaysinghx.portalsso.analytics.service.LoginEventRecorder;
import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.security.device.SignInNotifier;
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
 * Records login outcomes against the user row, audit log, and locks an account after too many consecutive
 * failures.
 */
@Component
public class LoginAttemptListener {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptListener.class);

    private final UserRepository userRepository;
    private final LoginEventRecorder loginEventRecorder;
    private final AuditService auditService;
    private final SignInNotifier signInNotifier;
    private final int maxFailedAttempts;

    public LoginAttemptListener(
            UserRepository userRepository,
            LoginEventRecorder loginEventRecorder,
            AuditService auditService,
            SignInNotifier signInNotifier,
            @Value("${app.security.max-failed-login-attempts:5}") int maxFailedAttempts) {
        this.userRepository = userRepository;
        this.loginEventRecorder = loginEventRecorder;
        this.auditService = auditService;
        this.signInNotifier = signInNotifier;
        this.maxFailedAttempts = maxFailedAttempts;
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        userRepository.findByEmail(username).ifPresentOrElse(user -> {
            loginEventRecorder.record(username, true, user.getId().toString());
            auditService.record(AuditAction.USER_LOGIN_SUCCESS, user.getId(), username, "Successful sign-in");
            user.setLastLoginAt(Instant.now());
            if (user.getFailedLoginAttempts() != 0) {
                user.setFailedLoginAttempts(0);
            }
            userRepository.save(user);

            // Last, and deliberately so: everything above is security-critical bookkeeping, and
            // this is advisory. SignInNotifier swallows its own failures, so a broken relay cannot
            // undo the lockout reset or the audit entry that have already been written here.
            signInNotifier.onSuccessfulSignIn(user);
        },
        () -> {
            loginEventRecorder.record(username, true, null);
            auditService.record(AuditAction.USER_LOGIN_SUCCESS, (String) null, username, "Successful sign-in");
        });
    }

    @EventListener
    @Transactional
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        if (username == null) {
            return;
        }

        userRepository.findByEmail(username).ifPresentOrElse(user -> {
            loginEventRecorder.record(username, false, user.getId().toString());
            auditService.record(
                    AuditAction.USER_LOGIN_FAILED,
                    user.getId(),
                    username,
                    "Failed sign-in attempt: " + event.getException().getMessage());
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
        () -> {
            loginEventRecorder.record(username, false, null);
            auditService.record(
                    AuditAction.USER_LOGIN_FAILED,
                    (String) null,
                    username,
                    "Failed sign-in attempt: " + event.getException().getMessage());
        });
    }
}
