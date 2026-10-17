package com.tanmaysinghx.portalsso.security.config;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Makes the session cookie {@code Secure} whenever the server is published over HTTPS.
 *
 * <p>The standalone jar must work on {@code http://localhost} out of the box, where a Secure
 * cookie would never be sent back and sign-in would silently loop. But a jar deployed behind TLS
 * that kept that default would hand out session cookies browsers will also send over plain http.
 * Deriving the default from {@code ISSUER_URL} gets both right without operator action.
 *
 * <p>Only a default: an explicit {@code SERVER_SERVLET_SESSION_COOKIE_SECURE} always wins.
 * {@code application.yml} reads it as {@code ${SERVER_SERVLET_SESSION_COOKIE_SECURE:${portal.session.secure-cookie-default:false}}}.
 */
public class SecureCookieEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String DEFAULT_PROPERTY = "portal.session.secure-cookie-default";
    private static final String ISSUER_PROPERTY = "spring.security.oauth2.authorizationserver.issuer";

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String issuer = environment.getProperty(ISSUER_PROPERTY, "");
        boolean https = issuer.trim().toLowerCase().startsWith("https://");
        environment.getPropertySources().addLast(
                new MapPropertySource("portalSecureCookieDefault", Map.of(DEFAULT_PROPERTY, String.valueOf(https))));
    }
}
