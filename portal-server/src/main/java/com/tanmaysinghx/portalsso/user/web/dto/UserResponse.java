package com.tanmaysinghx.portalsso.user.web.dto;

import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import java.time.Instant;
import java.util.List;

/** Never carries {@code passwordHash} or {@code mfaSecret}. */
public record UserResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        boolean accountLocked,
        boolean mfaEnabled,
        List<String> roles,
        Instant lastLoginAt,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isEnabled(),
                user.isAccountLocked(),
                user.isMfaEnabled(),
                user.getRoles().stream().map(Role::getName).sorted().toList(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
