package com.tanmaysinghx.portalsso.audit.service;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.entity.AuditEvent;
import com.tanmaysinghx.portalsso.audit.repository.AuditEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records administrative changes to {@code audit_events}.
 *
 * <h2>Why this does not swallow failures</h2>
 *
 * <p>{@link com.tanmaysinghx.portalsso.analytics.service.LoginEventRecorder} deliberately catches
 * everything: analytics must never be able to break authentication. This class takes the opposite
 * position on purpose. An audit trail exists to be complete, and a privileged change that happened
 * with no record of it is worse than a change that failed and can be retried — so a failed write
 * propagates.
 *
 * <p>{@link Propagation#REQUIRED} is the other half of that guarantee: joining the caller's
 * transaction means the audit row and the change it describes commit together or not at all. With
 * {@code REQUIRES_NEW} an audit entry could survive a rolled-back operation, which would be worse
 * than no entry — a log describing changes that never happened is one nobody can trust.
 */
@Service
public class AuditService {

    private static final int MAX_USER_AGENT = 512;
    private static final int MAX_DETAILS = 1000;
    private static final int MAX_TARGET_LABEL = 255;

    /** Stored as the actor when nobody is authenticated, so {@code actor_email} is never null. */
    static final String ANONYMOUS_ACTOR = "anonymous";

    /**
     * The application acting on its own behalf, with no request in flight — currently only the
     * startup bootstrap. Distinct from {@link #ANONYMOUS_ACTOR}, which means an unauthenticated
     * <em>caller</em>: recording a startup action as "anonymous" would imply someone reached the
     * server over HTTP to do it, which is the opposite of what happened.
     */
    public static final String SYSTEM_ACTOR = "system";

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    /**
     * @param details a short human-readable summary of what changed. Never pass a credential —
     *     name the field that changed, not its value.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, UUID targetId, String targetLabel, String details) {
        record(action, targetId == null ? null : targetId.toString(), targetLabel, details);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, String targetId, String targetLabel, String details) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        recordAs(actorEmail(authentication), action, targetId, targetLabel, details);
    }

    /**
     * Records an action taken by the application itself rather than by a caller — pass
     * {@link #SYSTEM_ACTOR}. Kept separate from {@link #record} so an ordinary request path cannot
     * accidentally attribute a change to anything but the authenticated user who made it.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSystemAction(AuditAction action, UUID targetId, String targetLabel, String details) {
        recordAs(SYSTEM_ACTOR, action, targetId == null ? null : targetId.toString(), targetLabel, details);
    }

    private void recordAs(String actorEmail, AuditAction action, String targetId, String targetLabel, String details) {
        HttpServletRequest request = currentRequest();
        String ipAddress = null;
        String userAgent = null;
        if (request != null) {
            // getRemoteAddr() rather than reading X-Forwarded-For directly — trusting that header
            // unconditionally would let a caller forge the source address of its own audit entry.
            // Behind a proxy, set server.forward-headers-strategy=FRAMEWORK and Spring populates
            // getRemoteAddr() from it, with the trust decision made once in configuration.
            ipAddress = request.getRemoteAddr();
            userAgent = truncate(request.getHeader("User-Agent"), MAX_USER_AGENT);
        }

        repository.save(new AuditEvent(
                // actor_id is left null: the principal is a UserDetails carrying only the email, so
                // filling it would cost a user lookup on every write. actor_email identifies the
                // actor either way, and unlike an id it stays meaningful if that account is deleted.
                null,
                actorEmail,
                action,
                targetId,
                truncate(targetLabel, MAX_TARGET_LABEL),
                truncate(details, MAX_DETAILS),
                ipAddress,
                userAgent,
                Instant.now()));
    }

    private static String actorEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ANONYMOUS_ACTOR;
        }
        // "anonymousUser" is what AnonymousAuthenticationToken reports; it is authenticated in the
        // Spring sense but is not a person, so it maps to the same placeholder.
        String name = authentication.getName();
        return (name == null || name.isBlank() || "anonymousUser".equals(name)) ? ANONYMOUS_ACTOR : name;
    }

    private static HttpServletRequest currentRequest() {
        // Not every audited change arrives over HTTP (seeding, tests, future background jobs), so
        // the absence of a request is normal rather than an error.
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
