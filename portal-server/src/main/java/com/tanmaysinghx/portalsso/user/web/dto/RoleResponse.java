package com.tanmaysinghx.portalsso.user.web.dto;

import com.tanmaysinghx.portalsso.user.entity.Role;
import java.time.Instant;

public record RoleResponse(
        String id,
        String name,
        String description,
        long userCount,
        /** Platform roles the application itself depends on; the console hides their delete action. */
        boolean protectedRole,
        Instant createdAt) {

    public static RoleResponse from(Role role, long userCount, boolean protectedRole) {
        return new RoleResponse(
                role.getId().toString(),
                role.getName(),
                role.getDescription(),
                userCount,
                protectedRole,
                role.getCreatedAt());
    }
}
