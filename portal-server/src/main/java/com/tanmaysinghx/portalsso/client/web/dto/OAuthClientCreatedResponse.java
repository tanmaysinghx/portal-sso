package com.tanmaysinghx.portalsso.client.web.dto;

/**
 * The create response, and the only place a client secret is ever visible.
 *
 * <p>A separate type from {@link OAuthClientResponse} on purpose: the list endpoint reuses that one,
 * so a secret field cannot leak into it by accident. {@code clientSecret} is null for public
 * (PKCE) clients.
 */
public record OAuthClientCreatedResponse(OAuthClientResponse client, String clientSecret) {}
