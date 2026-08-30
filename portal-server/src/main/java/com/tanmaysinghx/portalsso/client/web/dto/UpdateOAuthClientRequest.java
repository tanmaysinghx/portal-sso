package com.tanmaysinghx.portalsso.client.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Note the absence of {@code clientId}: it is deliberately immutable. Every relying application is
 * configured with it, so changing it would silently break each one with no migration path. To
 * change a client's identifier, register a new client and retire the old one.
 */
public record UpdateOAuthClientRequest(
        @NotBlank @Size(max = 200) String clientName,
        @NotEmpty List<@NotBlank String> redirectUris,
        @NotEmpty List<@NotBlank String> scopes,
        boolean enabled) {}
