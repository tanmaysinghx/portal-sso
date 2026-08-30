package com.tanmaysinghx.portalsso.security.ratelimit;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    /**
     * Spring Security's filter chain runs at order -100. Registering below that puts the limiter
     * ahead of authentication, so a flood is rejected on a map lookup rather than after a password
     * hash — and so the authorization-server endpoints are covered too, since they live in a
     * different security chain.
     *
     * <p>Registered explicitly rather than as a {@code @Component}: an auto-detected filter lands at
     * lowest precedence, which would place it after the very chains it is meant to protect.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitProperties properties, RateLimiter rateLimiter) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(properties, rateLimiter));
        registration.setOrder(-200);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
