package com.tanmaysinghx.portalsso.security.ratelimit;

import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests that exceed the configured per-IP rate for a matched path.
 *
 * <p>Runs ahead of the Spring Security chains so it also covers the authorization-server endpoints,
 * and so a flood costs nothing more than a map lookup — authenticating first would defeat the point
 * of a limiter.
 *
 * <p>The client address comes from {@code getRemoteAddr()}, not a hand-parsed
 * {@code X-Forwarded-For}. Trusting that header unconditionally would let a caller spoof a fresh
 * source address per request and bypass the limiter entirely. Behind a proxy, set
 * {@code server.forward-headers-strategy=FRAMEWORK} so the container resolves it once, from
 * configuration.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties properties;
    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimitProperties properties, RateLimiter rateLimiter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!properties.enabled()) {
            chain.doFilter(request, response);
            return;
        }

        Map.Entry<String, RateLimitProperties.Rule> match = longestPrefixMatch(request.getRequestURI());
        if (match == null) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfter = rateLimiter.checkAndConsume(match.getKey(), match.getValue(), request.getRemoteAddr());
        if (retryAfter == 0) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit hit for {} from {} — retry in {}s", match.getKey(), request.getRemoteAddr(), retryAfter);
        writeTooManyRequests(response, retryAfter);
    }

    /**
     * Longest prefix wins so a narrow rule beats a broad one regardless of map order — otherwise
     * a rule on {@code /api} would silently shadow a stricter one on {@code /api/public/register}.
     */
    private Map.Entry<String, RateLimitProperties.Rule> longestPrefixMatch(String uri) {
        Map.Entry<String, RateLimitProperties.Rule> best = null;
        for (Map.Entry<String, RateLimitProperties.Rule> entry : properties.rules().entrySet()) {
            if (uri.startsWith(entry.getKey()) && (best == null || entry.getKey().length() > best.getKey().length())) {
                best = entry;
            }
        }
        return best;
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Shaped like the rest of the API's errors so a client parses one format, not two.
        response.getWriter().write(
                """
                {"code":"%s","message":"Too many requests. Try again in %d seconds.","status":429}"""
                        .formatted(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(), retryAfter));
    }
}
