package com.tanmaysinghx.portalsso.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP address from an HTTP request.
 *
 * <p>When running behind a reverse proxy or Docker bridge network (e.g. Nginx, Cloudflare, Traefik,
 * Docker gateway 172.19.0.1), {@link HttpServletRequest#getRemoteAddr()} returns the proxy IP.
 * This utility inspects {@code X-Forwarded-For} and {@code X-Real-IP} headers to extract the true
 * client IP address.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] ips = xForwardedFor.split(",");
            for (String ip : ips) {
                String candidate = ip.trim();
                if (isValidIpCandidate(candidate)) {
                    return candidate;
                }
            }
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            String candidate = xRealIp.trim();
            if (isValidIpCandidate(candidate)) {
                return candidate;
            }
        }

        return request.getRemoteAddr();
    }

    private static boolean isValidIpCandidate(String ip) {
        return ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip);
    }
}
