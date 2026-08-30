package com.tanmaysinghx.portalsso.analytics.service;

import com.tanmaysinghx.portalsso.analytics.entity.LoginEvent;
import com.tanmaysinghx.portalsso.analytics.geo.GeoIpResolver;
import com.tanmaysinghx.portalsso.analytics.repository.LoginEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes one {@link LoginEvent} per sign-in attempt, enriching it with the caller's IP, user agent
 * and resolved country.
 *
 * <p>The IP comes from {@code getRemoteAddr()} rather than parsing {@code X-Forwarded-For} by hand.
 * Trusting that header unconditionally would let any client spoof its own source address, which
 * would poison the audit trail this table exists to be. Behind a reverse proxy, set
 * {@code server.forward-headers-strategy=FRAMEWORK} and Spring populates {@code getRemoteAddr()}
 * from the forwarded headers for you, with the trust decision made once, in configuration.
 */
@Service
public class LoginEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoginEventRecorder.class);
    private static final int MAX_USER_AGENT = 512;

    private final LoginEventRepository repository;
    private final GeoIpResolver geoIpResolver;

    public LoginEventRecorder(LoginEventRepository repository, GeoIpResolver geoIpResolver) {
        this.repository = repository;
        this.geoIpResolver = geoIpResolver;
    }

    /**
     * Never propagates a failure. Analytics must not be able to break authentication: if writing
     * the event fails, the sign-in itself still stands.
     */
    @Transactional
    public void record(String email, boolean successful, String userId) {
        try {
            LoginEvent event = new LoginEvent(email, successful, Instant.now());
            event.setUserId(userId);

            HttpServletRequest request = currentRequest();
            if (request != null) {
                String ip = request.getRemoteAddr();
                event.setIpAddress(ip);
                event.setUserAgent(truncate(request.getHeader("User-Agent")));
                event.setClientId(request.getParameter("client_id"));

                GeoIpResolver.GeoLocation location = geoIpResolver.resolve(ip);
                event.setCountryCode(location.code());
                event.setCountryName(location.name());
                event.setCity(location.city());
            }

            repository.save(event);
        } catch (Exception e) {
            log.warn("Could not record login event for '{}': {}", email, e.getMessage());
        }
    }

    private static HttpServletRequest currentRequest() {
        // Authentication events also fire outside a servlet request (tests, background flows), so
        // the absence of one is normal rather than an error.
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_USER_AGENT ? value : value.substring(0, MAX_USER_AGENT);
    }
}
