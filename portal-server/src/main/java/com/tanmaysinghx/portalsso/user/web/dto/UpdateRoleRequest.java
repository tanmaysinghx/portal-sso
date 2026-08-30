package com.tanmaysinghx.portalsso.user.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Note the absence of {@code name}: a role's name is deliberately immutable, for the same reason
 * {@code clientId} is. It is the granted authority, it is written into the {@code roles} claim of
 * every issued JWT, and relying applications authorize against it. Renaming {@code ROLE_ADMIN}
 * would sign every administrator out of the console and silently break authorization in every app
 * behind this server, with no migration path. To reshape roles, create the new one, reassign, and
 * delete the old.
 */
public record UpdateRoleRequest(@Size(max = 255) String description) {}
