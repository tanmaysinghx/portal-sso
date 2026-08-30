package com.tanmaysinghx.portalsso.security;

import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/**
 * Adds profile/email/role claims to the ID token and access token, sourced from {@link User}.
 * Without this, tokens carry only {@code sub} — every relying app behind the portal needs at
 * least email and role to make its own authorization decisions, and the userinfo endpoint mirrors
 * whatever claims are on the ID token.
 */
@Configuration
public class JwtClaimsCustomizerConfig {

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtClaimsCustomizer(UserRepository userRepository) {
        return context -> {
            boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
            boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
            if (!idToken && !accessToken) {
                return;
            }

            Optional<User> user = userRepository.findByEmail(context.getPrincipal().getName());
            if (user.isEmpty()) {
                return;
            }
            addClaims(context.getClaims(), user.get(), context.getAuthorizedScopes());
        };
    }

    private void addClaims(JwtClaimsSet.Builder claims, User user, java.util.Set<String> authorizedScopes) {
        List<String> roles = new java.util.ArrayList<>(user.getRoles().stream().map(Role::getName).toList());
        claims.claim("roles", roles);

        if (authorizedScopes.contains(OidcScopes.EMAIL)) {
            claims.claim(StandardClaimNames.EMAIL, user.getEmail());
        }

        if (authorizedScopes.contains(OidcScopes.PROFILE)) {
            if (user.getFirstName() != null) {
                claims.claim(StandardClaimNames.GIVEN_NAME, user.getFirstName());
            }
            if (user.getLastName() != null) {
                claims.claim(StandardClaimNames.FAMILY_NAME, user.getLastName());
            }
            String fullName = fullName(user);
            if (fullName != null) {
                claims.claim(StandardClaimNames.NAME, fullName);
            }
        }
    }

    private String fullName(User user) {
        List<String> parts = new java.util.ArrayList<>();
        if (user.getFirstName() != null) {
            parts.add(user.getFirstName());
        }
        if (user.getLastName() != null) {
            parts.add(user.getLastName());
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }
}
