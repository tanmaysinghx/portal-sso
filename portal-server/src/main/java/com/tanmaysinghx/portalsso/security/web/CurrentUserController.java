package com.tanmaysinghx.portalsso.security.web;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the admin dashboard SPA tell apart "not logged in" (401, handled by {@link
 * com.tanmaysinghx.portalsso.security.SecurityConfig}'s API entry point) from "logged in but not
 * an admin" (200 with no {@code ROLE_ADMIN} in {@code roles}) on load/refresh, without probing a
 * role-gated endpoint to find out.
 */
@RestController
@RequestMapping("/api/admin/me")
public class CurrentUserController {

    @GetMapping
    public CurrentUserResponse me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return new CurrentUserResponse(authentication.getName(), roles);
    }

    public record CurrentUserResponse(String email, List<String> roles) {}
}
