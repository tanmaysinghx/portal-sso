package com.tanmaysinghx.portalsso.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class ClientIpResolverTest {

    @Test
    void returnsNullWhenRequestIsNull() {
        assertThat(ClientIpResolver.getClientIp(null)).isNull();
    }

    @Test
    void returnsRemoteAddrWhenNoForwardedHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");

        assertThat(ClientIpResolver.getClientIp(request)).isEqualTo("192.168.1.10");
    }

    @Test
    void extractsFirstIpFromXForwardedFor() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("172.19.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195, 172.19.0.1");

        assertThat(ClientIpResolver.getClientIp(request)).isEqualTo("203.0.113.195");
    }

    @Test
    void extractsXRealIpWhenNoXForwardedFor() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("172.19.0.1");
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.42");

        assertThat(ClientIpResolver.getClientIp(request)).isEqualTo("198.51.100.42");
    }
}
