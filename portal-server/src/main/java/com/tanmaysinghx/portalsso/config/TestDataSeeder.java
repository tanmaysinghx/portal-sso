package com.tanmaysinghx.portalsso.config;

import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a test user and a hardcoded, PKCE-only public test OAuth client so the
 * authorization_code + PKCE flow can be exercised end to end against a real user in the
 * database. Dev/test convenience only — disabled by default; enable with
 * {@code app.seed.test-data=true}. Not how the admin dashboard will register real clients later
 * (that goes through {@link RegisteredClientRepository} directly, same as this seeder does).
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "test-data", havingValue = "true")
// Pinned ahead of AdminBootstrapper (@Order(100)) so that in development the admin this seeds
// already exists by the time the bootstrapper checks whether the server is administrable.
@Order(0)
public class TestDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestDataSeeder.class);

    public static final String TEST_USER_EMAIL = "testuser@portalsso.local";
    public static final String TEST_USER_PASSWORD = "TestPassword123!";
    public static final String TEST_ADMIN_EMAIL = "admin@portalsso.local";
    public static final String TEST_ADMIN_PASSWORD = "AdminPassword123!";
    public static final String TEST_CLIENT_ID = "test-client";
    public static final String TEST_CLIENT_REDIRECT_URI = "http://127.0.0.1:8080/authorized";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RegisteredClientRepository registeredClientRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.registeredClientRepository = registeredClientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedTestUser();
        seedAdminUser();
        seedTestClient();
    }

    private void seedTestUser() {
        if (userRepository.existsByEmail(TEST_USER_EMAIL)) {
            return;
        }
        Role role = roleRepository.findByName("ROLE_USER").orElseGet(() -> roleRepository.save(new Role("ROLE_USER", "Standard user")));

        User user = new User(TEST_USER_EMAIL, passwordEncoder.encode(TEST_USER_PASSWORD));
        user.addRole(role);
        userRepository.save(user);
        log.info("Seeded test user '{}' / '{}'", TEST_USER_EMAIL, TEST_USER_PASSWORD);
    }

    private void seedAdminUser() {
        if (userRepository.existsByEmail(TEST_ADMIN_EMAIL)) {
            return;
        }
        Role role = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN", "Portal administrator")));

        User admin = new User(TEST_ADMIN_EMAIL, passwordEncoder.encode(TEST_ADMIN_PASSWORD));
        admin.addRole(role);
        userRepository.save(admin);
        log.info("Seeded admin user '{}' / '{}'", TEST_ADMIN_EMAIL, TEST_ADMIN_PASSWORD);
    }

    private void seedTestClient() {
        if (registeredClientRepository.findByClientId(TEST_CLIENT_ID) != null) {
            return;
        }

        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(TEST_CLIENT_ID)
                .clientName("Test Client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(TEST_CLIENT_REDIRECT_URI)
                .scope("openid")
                .scope("profile")
                .scope("email")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        registeredClientRepository.save(client);
        log.info("Seeded test OAuth client '{}' (public, PKCE-only, redirect_uri={})", TEST_CLIENT_ID, TEST_CLIENT_REDIRECT_URI);
    }
}
