package com.tanmaysinghx.portalsso.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SecureCookieEnvironmentPostProcessorTest {

    private final SecureCookieEnvironmentPostProcessor processor = new SecureCookieEnvironmentPostProcessor();

    @Test
    void httpsIssuerDefaultsToSecureCookies() {
        assertThat(defaultFor("https://sso.example.com")).isEqualTo("true");
    }

    @Test
    void plainHttpIssuerKeepsCookiesUsableOnLocalhost() {
        assertThat(defaultFor("http://localhost:8080")).isEqualTo("false");
    }

    @Test
    void missingIssuerIsTreatedAsPlainHttp() {
        assertThat(defaultFor(null)).isEqualTo("false");
    }

    @Test
    void explicitOverrideStillWins() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.security.oauth2.authorizationserver.issuer", "https://sso.example.com")
                .withProperty("SERVER_SERVLET_SESSION_COOKIE_SECURE", "false");
        processor.postProcessEnvironment(env, null);

        String resolved = env.resolvePlaceholders(
                "${SERVER_SERVLET_SESSION_COOKIE_SECURE:${" + SecureCookieEnvironmentPostProcessor.DEFAULT_PROPERTY + ":false}}");
        assertThat(resolved).isEqualTo("false");
    }

    private String defaultFor(String issuer) {
        MockEnvironment env = new MockEnvironment();
        if (issuer != null) {
            env.setProperty("spring.security.oauth2.authorizationserver.issuer", issuer);
        }
        processor.postProcessEnvironment(env, null);
        return env.getProperty(SecureCookieEnvironmentPostProcessor.DEFAULT_PROPERTY);
    }
}
