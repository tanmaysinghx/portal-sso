package com.tanmaysinghx.portalsso.security;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/**
 * Hand-rolled replacement for Boot's autoconfigured authorization-server security ({@code
 * OAuth2AuthorizationServerWebSecurityConfiguration}, which backs off entirely once any {@link
 * SecurityFilterChain} bean is defined). Reproduces that default wiring as-is and layers in the
 * one thing it can't provide: cookie-based CSRF for the admin dashboard SPA — a plain {@code
 * XSRF-TOKEN} cookie the SPA reads and echoes back as {@code X-XSRF-TOKEN}, which is what
 * Angular's {@code HttpClient} does automatically.
 */
@Configuration
@EnableMethodSecurity
@EnableJdbcHttpSession
public class SecurityConfig {

    /**
     * Static console assets, ahead of everything else.
     *
     * <p>Without this chain every request for a JavaScript bundle went through the full security
     * filter chain, which reads the {@code SecurityContext} from the session — and with Spring
     * Session JDBC that is a database round-trip, plus a write-back, <em>per asset</em>. Measured
     * against a remote database that was <strong>four SQL statements and ~770ms to serve one .js
     * file</strong>, which is most of why first paint took over five seconds.
     *
     * <p>Nothing here is protected content: it is the same bundle every visitor downloads before
     * authenticating. A {@link NullSecurityContextRepository} plus {@code STATELESS} guarantees no
     * session is read or created, while still applying the standard security response headers —
     * which is why this is a filter chain rather than {@code web.ignoring()}.
     */
    @Bean
    @Order(0)
    SecurityFilterChain staticResourceSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/*.js", "/*.css", "/*.svg", "/*.png", "/*.jpg", "/*.webp",
                        "/*.ico", "/*.json", "/*.txt", "/*.woff2", "/media/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                // No session read, no session write, no session created.
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Nothing here is state-changing, and a CSRF token load would itself touch the session.
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(RequestCacheConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                // Spring Security stamps "no-cache, no-store, max-age=0, must-revalidate" on every
                // response by default. That is right for an authenticated page and wrong for a
                // content-hashed bundle: it made the browser re-download every chunk on every
                // navigation. Angular puts a content hash in each filename, so a changed file is a
                // different URL and these can be cached indefinitely.
                .headers(headers -> headers
                        .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Cache-Control", "public, max-age=31536000, immutable")));

        return http.build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // getEndpointsMatcher() defers to this exact instance's state, populated only once it is
        // init()-ed as part of http.build() — it must be the same instance wired in below via
        // apply(), not a separate one (e.g. from oauth2AuthorizationServer(customizer), which
        // manages its own instance internally), or the matcher NPEs on first use.
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher());
        http.apply(authorizationServerConfigurer);
        authorizationServerConfigurer
                .oidc(Customizer.withDefaults())
                // Only reached for clients registered with requireAuthorizationConsent(true);
                // clients without it still skip straight through, as before.
                .authorizationEndpoint(endpoint -> endpoint.consentPage("/oauth2/consent"));

        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http,
            PortalUserDetailsService userDetailsService,
            com.tanmaysinghx.portalsso.security.mfa.MfaAuthenticationSuccessHandler mfaSuccessHandler,
            @Value("${app.security.remember-me-key:portal-sso-remember-me-key-3b71e86a}") String rememberMeKey) throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                // formLogin's entry point always redirects to /login, which is wrong for the admin
                // console's fetch()/XHR calls under /api/** — they need a plain 401 they can branch
                // on, not a redirect silently followed to an HTML page. This is registered as a
                // scoped default, the same mechanism formLogin() uses for its own entry point,
                // rather than replacing the entry point outright: a blanket
                // .authenticationEntryPoint(single) makes FormLoginConfigurer treat the entry point
                // as fully customised, and the /login page stops being served at all.
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), PathPatternRequestMatcher.pathPattern("/api/**")))
                .authorizeHttpRequests(authorize -> authorize
                        // Must precede the /api/** rule — rules match in order, so the broad
                        // authenticated() below would otherwise swallow these. /api/public/** is
                        // the only unauthenticated API surface: self-registration and the policy
                        // lookup the sign-in page uses to decide whether to offer a sign-up link.
                        // It stays CSRF-protected like every other endpoint.
                        .requestMatchers(PathPatternRequestMatcher.pathPattern("/api/public/**")).permitAll()
                        .requestMatchers(PathPatternRequestMatcher.pathPattern("/mfa-challenge")).permitAll()
                        .requestMatchers(PathPatternRequestMatcher.pathPattern("/api/**")).authenticated()
                        // The consent screen names the application and the signed-in user, so it is
                        // only meaningful — and only safe to render — for an authenticated session.
                        .requestMatchers(PathPatternRequestMatcher.pathPattern("/oauth2/consent")).authenticated()
                        .anyRequest().permitAll())
                // Naming a login page is what stops DefaultLoginPageGeneratingFilter emitting the
                // stock unstyled form; AuthPageController serves the branded one at the same path,
                // so the POST target and CSRF handling are unchanged.
                .formLogin(form -> form.loginPage("/login").successHandler(mfaSuccessHandler).permitAll())
                .rememberMe(rememberMe -> rememberMe
                        .userDetailsService(userDetailsService)
                        .key(rememberMeKey)
                        .tokenValiditySeconds(30 * 24 * 60 * 60)
                        .rememberMeParameter("remember-me"));

        return http.build();
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcOperations jdbcOperations, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcOperations jdbcOperations, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return NimbusJwtDecoder.withJwkSource(jwkSource).build();
    }

    /**
     * The CSRF token is loaded lazily; nothing reads it (and so nothing writes the cookie) on a
     * request that isn't rendering the server-side login form. This forces that read on every
     * request so the SPA always has a fresh {@code XSRF-TOKEN} cookie to send back.
     */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
