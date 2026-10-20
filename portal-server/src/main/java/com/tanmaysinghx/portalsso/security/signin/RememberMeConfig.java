package com.tanmaysinghx.portalsso.security.signin;

import com.tanmaysinghx.portalsso.security.PortalUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One remember-me service, shared by both filter chains and by {@link SignInCompletion}.
 *
 * <p>It is a bean rather than something each {@code .rememberMe()} DSL call builds privately because
 * sign-in needs to reach it directly: a password alone must not mint the cookie when a second step
 * (MFA, or an expired password) is still to come — see {@link PortalRememberMeServices}.
 */
@Configuration
public class RememberMeConfig {

    public static final String PARAMETER = "remember-me";
    private static final int VALIDITY_SECONDS = 30 * 24 * 60 * 60;

    @Bean
    public PortalRememberMeServices rememberMeServices(
            PortalUserDetailsService userDetailsService,
            SecondStep secondStep,
            @Value("${app.security.remember-me-key:portal-sso-remember-me-key-3b71e86a}") String rememberMeKey) {
        PortalRememberMeServices services = new PortalRememberMeServices(rememberMeKey, userDetailsService, secondStep);
        services.setTokenValiditySeconds(VALIDITY_SECONDS);
        services.setParameter(PARAMETER);
        return services;
    }
}
