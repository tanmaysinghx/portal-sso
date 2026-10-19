package com.tanmaysinghx.portalsso.security.device;

import com.tanmaysinghx.portalsso.analytics.geo.GeoIpResolver;
import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.common.web.ClientIpResolver;
import com.tanmaysinghx.portalsso.mail.service.NotificationService;
import com.tanmaysinghx.portalsso.mail.service.SignInDetails;
import com.tanmaysinghx.portalsso.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Decides which sign-in email a successful authentication earns, and records a new device.
 *
 * <h2>Why this is a separate component</h2>
 *
 * <p>{@code LoginAttemptListener} already runs on every sign-in, and this could have been three
 * more statements inside it. It is separate because the two have different failure contracts: that
 * listener maintains the lockout counter and the audit trail, where a failure must propagate, while
 * everything here is advisory and must never be able to fail a sign-in. Keeping them apart makes
 * the swallow-everything boundary explicit instead of burying it in a method that also does
 * security-critical work.
 *
 * <h2>Why at most one email</h2>
 *
 * <p>A first sign-in from a new device would otherwise match both the new-device alert and the
 * every-sign-in notice, and the pair arrive together describing the same event. The alert is the
 * more informative of the two, so it wins and the notice is suppressed.
 */
@Component
public class SignInNotifier {

    private static final Logger log = LoggerFactory.getLogger(SignInNotifier.class);

    private final KnownDeviceService knownDeviceService;
    private final NotificationService notificationService;
    private final GeoIpResolver geoIpResolver;
    private final AuditService auditService;

    public SignInNotifier(
            KnownDeviceService knownDeviceService,
            NotificationService notificationService,
            GeoIpResolver geoIpResolver,
            AuditService auditService) {
        this.knownDeviceService = knownDeviceService;
        this.notificationService = notificationService;
        this.geoIpResolver = geoIpResolver;
        this.auditService = auditService;
    }

    /**
     * Never propagates a failure. A notification nobody is blocked on must not be able to break the
     * sign-in that triggered it — the same stance {@code LoginEventRecorder} takes.
     */
    public void onSuccessfulSignIn(User user) {
        try {
            HttpServletRequest request = currentRequest();
            String userAgent = request == null ? null : request.getHeader("User-Agent");
            String ip = request == null ? null : ClientIpResolver.getClientIp(request);
            String location = resolveLocation(ip);

            KnownDeviceService.Outcome outcome =
                    knownDeviceService.recordSignIn(user.getId(), userAgent, ip, location);

            SignInDetails details = SignInDetails.of(outcome.deviceLabel(), ip, location);

            if (outcome.firstSeen()) {
                // Audited as well as emailed. The email can be switched off or fail to deliver; the
                // audit entry is what lets an administrator reconstruct the event afterwards either
                // way, which is the whole point of having a trail.
                auditService.record(
                        AuditAction.NEW_DEVICE_SIGN_IN,
                        user.getId(),
                        user.getEmail(),
                        "device=" + outcome.deviceLabel() + (ip == null ? "" : ", ip=" + ip));
                notificationService.sendNewDeviceAlert(user.getEmail(), user.getFirstName(), details);
            } else {
                notificationService.sendSignInNotice(user.getEmail(), user.getFirstName(), details);
            }
        } catch (Exception e) {
            log.warn("Could not process sign-in notifications for '{}': {}", user.getEmail(), e.getMessage());
        }
    }

    /** Country and city, formatted, or null when GeoIP is not configured — which is the default. */
    private String resolveLocation(String ip) {
        if (ip == null) {
            return null;
        }
        GeoIpResolver.GeoLocation location = geoIpResolver.resolve(ip);
        String country = location.name();
        String city = location.city();
        boolean hasCountry = country != null && !country.isBlank() && !"Unknown".equalsIgnoreCase(country);
        boolean hasCity = city != null && !city.isBlank() && !"Unknown".equalsIgnoreCase(city);

        if (hasCity && hasCountry) {
            return city + ", " + country;
        }
        if (hasCountry) {
            return country;
        }
        // Returns null rather than "Unknown": the templates omit the line entirely when there is no
        // location, which reads better than a row that says nothing.
        return null;
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
