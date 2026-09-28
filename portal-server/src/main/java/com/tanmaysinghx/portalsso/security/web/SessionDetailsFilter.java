package com.tanmaysinghx.portalsso.security.web;

import com.tanmaysinghx.portalsso.common.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tracks the latest IP Address and User-Agent for active Spring Sessions.
 * Only dirties the session if these values change, ensuring we don't cause unnecessary DB writes.
 */
@Component
public class SessionDetailsFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session != null) {
            String ip = ClientIpResolver.getClientIp(request);
            String ua = request.getHeader(HttpHeaders.USER_AGENT);
            if (ua == null) {
                ua = "Unknown Device";
            }

            if (!ip.equals(session.getAttribute("ipAddress"))) {
                session.setAttribute("ipAddress", ip);
            }
            if (!ua.equals(session.getAttribute("userAgent"))) {
                session.setAttribute("userAgent", ua);
            }
        }

        filterChain.doFilter(request, response);
    }
}
