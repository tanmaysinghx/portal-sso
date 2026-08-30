package com.tanmaysinghx.portalsso.user.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

/**
 * The complete set of roles the user should end up with, not a delta. A caller sending the whole
 * set cannot half-apply a change, and the audit entry can record a meaningful before/after.
 *
 * <p>{@code @NotEmpty} because an account with no roles authenticates successfully and then fails
 * every authorization check — it looks active and behaves like a dead account, which is a worse
 * outcome than rejecting the request.
 */
public record SetUserRolesRequest(@NotEmpty Set<String> roles) {}
