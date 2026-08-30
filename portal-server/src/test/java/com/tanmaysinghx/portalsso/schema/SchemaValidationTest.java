package com.tanmaysinghx.portalsso.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.config.JpaAuditingConfig;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the Liquibase changelog and JPA entity mappings together against an in-memory
 * database, so a mismatch between the two (e.g. a column Hibernate expects that the changelog
 * never creates) fails fast instead of surfacing only against a real Postgres/MySQL/Oracle
 * instance.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class SchemaValidationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OAuthClientRepository oAuthClientRepository;

    /**
     * Uses the role seeded by migration 011 rather than inserting one. That is not just to avoid
     * the unique-constraint clash: reading the seeded row back is what proves the literal UUID in
     * the changelog parses through {@code BaseEntity}'s {@code UUID} mapping, which a
     * VARCHAR(36) column will happily store and only fail on read.
     */
    @Test
    void persistsUserWithRoles() {
        Role role = roleRepository.findByName("ROLE_ADMIN").orElseThrow();

        User user = new User("admin@example.com", "hashed-password");
        user.addRole(role);
        User saved = userRepository.save(user);

        User found = userRepository.findByEmail("admin@example.com").orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getRoles()).extracting(Role::getName).containsExactly("ROLE_ADMIN");
    }

    @Test
    void theSeededPlatformRolesAreReadableThroughTheEntityMapping() {
        assertThat(roleRepository.findAllByOrderByNameAsc())
                .extracting(Role::getName)
                .contains("ROLE_ADMIN", "ROLE_USER");
        assertThat(roleRepository.findByName("ROLE_USER").orElseThrow().getId()).isNotNull();
    }

    @Test
    void persistsOAuthClient() {
        OAuthClient client = new OAuthClient(
                "test-client",
                "Test Client",
                "client_secret_basic",
                "authorization_code,refresh_token",
                "openid,profile,email",
                "{}",
                "{}");
        client.setRedirectUris("https://example.com/callback");

        OAuthClient saved = oAuthClientRepository.save(client);

        OAuthClient found = oAuthClientRepository.findByClientId("test-client").orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getRedirectUris()).isEqualTo("https://example.com/callback");
    }

    @Autowired
    private com.tanmaysinghx.portalsso.security.key.SigningKeyRepository signingKeyRepository;

    @Test
    void persistsSigningKey() {
        com.tanmaysinghx.portalsso.security.key.SigningKey key =
                new com.tanmaysinghx.portalsso.security.key.SigningKey(
                        "kid-123", "RS256", "public-pem", "private-pem", true);

        com.tanmaysinghx.portalsso.security.key.SigningKey saved = signingKeyRepository.save(key);

        com.tanmaysinghx.portalsso.security.key.SigningKey found =
                signingKeyRepository.findByKeyId("kid-123").orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getAlgorithm()).isEqualTo("RS256");
        assertThat(found.isActive()).isTrue();
    }
}
