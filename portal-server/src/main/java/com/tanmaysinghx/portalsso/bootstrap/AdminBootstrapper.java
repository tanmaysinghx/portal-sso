package com.tanmaysinghx.portalsso.bootstrap;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.service.RoleService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator on a deployment that has none.
 *
 * <p>Without this, a fresh deployment is unusable: Liquibase creates the schema and seeds the two
 * platform roles, but the {@code users} table is empty, {@code TestDataSeeder} is dev-only and off
 * by default, and self-registration only ever grants {@code ROLE_USER} — so the operator gets a
 * working server with no account to sign in to and no way to make one.
 *
 * <h2>Jenkins LTS-Style Initialization vs Explicit Configuration</h2>
 *
 * <p>Operators can provide explicit credentials via {@code APP_BOOTSTRAP_ADMIN_EMAIL} and
 * {@code APP_BOOTSTRAP_ADMIN_PASSWORD}. When running standalone without configured credentials,
 * if {@code autoGenerate} is enabled (the standalone default), Portal SSO provisions an
 * initial administrator with a high-entropy password printed to the console and saved to
 * {@code ~/.portal-sso/secrets/initialAdminPassword}, identical to Jenkins LTS first-run flow.
 *
 * <h2>Guarantees</h2>
 *
 * <ul>
 *   <li><strong>Zero static credentials.</strong> Any auto-generated password is created via
 *       {@link SecureRandom} at runtime and stored locally with 0600 permissions.
 *   <li><strong>Idempotent.</strong> It acts only when no <em>enabled</em> administrator exists.
 *   <li><strong>Never overwrites a password.</strong> If the address already has an account, the
 *       role is granted and the account enabled, but the existing password stands.
 * </ul>
 */
@Component
// Runs after TestDataSeeder, which is pinned to @Order(0). In development that seeds an
// administrator, and this then correctly finds one and stands down instead of warning about a
// lockout that is about to resolve itself a few milliseconds later.
@Order(100)
public class AdminBootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapper.class);

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%^&*-_+=";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Deliberately stricter than the 8 characters the ordinary user endpoints require. This is the
     * single most privileged account on the server and its password is chosen once, by an operator,
     * in a config file — none of the usability arguments for a lower bar apply.
     */
    static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final BootstrapProperties properties;
    private final PasswordPolicy passwordPolicy;

    @Value("${spring.security.oauth2.authorizationserver.issuer:http://localhost:8080}")
    private String issuerUrl;

    public AdminBootstrapper(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            BootstrapProperties properties,
            PasswordPolicy passwordPolicy) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.properties = properties;
        this.passwordPolicy = passwordPolicy;
    }

    public static Path resolvePortalHome() {
        String customHome = System.getProperty("portal.home");
        if (customHome == null || customHome.isBlank()) {
            customHome = System.getProperty("PORTAL_HOME");
        }
        if (customHome == null || customHome.isBlank()) {
            customHome = System.getenv("PORTAL_HOME");
        }
        if (customHome != null && !customHome.isBlank()) {
            return Path.of(customHome).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home", "."), ".portal-sso").toAbsolutePath().normalize();
    }

    static String generateSecurePassword() {
        List<Character> chars = new ArrayList<>(24);
        chars.add(UPPER.charAt(SECURE_RANDOM.nextInt(UPPER.length())));
        chars.add(UPPER.charAt(SECURE_RANDOM.nextInt(UPPER.length())));
        chars.add(LOWER.charAt(SECURE_RANDOM.nextInt(LOWER.length())));
        chars.add(LOWER.charAt(SECURE_RANDOM.nextInt(LOWER.length())));
        chars.add(DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length())));
        chars.add(DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length())));
        chars.add(SYMBOLS.charAt(SECURE_RANDOM.nextInt(SYMBOLS.length())));
        chars.add(SYMBOLS.charAt(SECURE_RANDOM.nextInt(SYMBOLS.length())));
        while (chars.size() < 24) {
            chars.add(ALL.charAt(SECURE_RANDOM.nextInt(ALL.length())));
        }
        Collections.shuffle(chars, SECURE_RANDOM);
        StringBuilder sb = new StringBuilder(chars.size());
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hasEnabledAdministrator()) {
            return;
        }

        if (!properties.isConfigured()) {
            if (properties.shouldAutoGenerate()) {
                bootstrapAutoGeneratedAdmin();
                return;
            }
            // Worth shouting about: the server starts cleanly and answers requests, so without this
            // the operator's only symptom is a sign-in page that rejects every credential they try.
            log.warn("""
                    No enabled administrator exists and no bootstrap credentials are configured, \
                    so nobody can sign in to the admin console. Set app.bootstrap.admin-email and \
                    app.bootstrap.admin-password (APP_BOOTSTRAP_ADMIN_EMAIL / \
                    APP_BOOTSTRAP_ADMIN_PASSWORD) and restart.""");
            return;
        }

        String email = properties.adminEmail().trim().toLowerCase(Locale.ROOT);
        String password = properties.adminPassword();

        // The configured policy, plus this class's own higher length floor. Refused rather than
        // accepted-with-a-warning: a warning scrolls past, and the result would be the most
        // privileged account on the server behind a weak password.
        List<String> failures = new java.util.ArrayList<>(passwordPolicy.failures(password));
        if (password.length() < MIN_PASSWORD_LENGTH) {
            failures.add("must be at least " + MIN_PASSWORD_LENGTH + " characters for the bootstrap administrator");
        }
        if (!failures.isEmpty()) {
            log.error(
                    "Refusing to bootstrap administrator '{}': app.bootstrap.admin-password {}. "
                            + "No account was created.",
                    email,
                    String.join("; ", failures));
            return;
        }

        Role adminRole = roleRepository.findByName(RoleService.ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        RoleService.ADMIN_ROLE + " is missing; migration 011 should have seeded it."));

        User user = userRepository.findByEmail(email).orElse(null);
        boolean existing = user != null;

        if (existing) {
            // The recovery path: every administrator was disabled, and the operator is pointing the
            // bootstrap at an account that already exists. Grant and enable, but leave the password
            // alone — resetting it from a config file would be a way to take over someone's account.
            user.setEnabled(true);
            user.addRole(adminRole);
            log.warn(
                    "Granted {} to existing account '{}' and enabled it, because no enabled administrator "
                            + "remained. The existing password was NOT changed.",
                    RoleService.ADMIN_ROLE,
                    email);
        } else {
            user = new User(email, passwordEncoder.encode(password));
            user.setEnabled(true);
            user.addRole(adminRole);
            roleRepository.findByName("ROLE_USER").ifPresent(user::addRole);
        }

        User saved = userRepository.save(user);

        auditService.recordSystemAction(
                AuditAction.ADMIN_BOOTSTRAPPED,
                saved.getId(),
                saved.getEmail(),
                existing ? "grantedAdminToExistingAccount=true" : "createdNewAccount=true");

        log.warn("""
                Bootstrapped administrator '{}' from app.bootstrap.*. Sign in, then REMOVE those \
                properties — while they remain set, anyone who can read your configuration knows \
                this account's password.""", email);
    }

    private void bootstrapAutoGeneratedAdmin() {
        String email = (properties.adminEmail() != null && !properties.adminEmail().isBlank())
                ? properties.adminEmail().trim().toLowerCase(Locale.ROOT)
                : "admin@localhost";
        String password = generateSecurePassword();

        Role adminRole = roleRepository.findByName(RoleService.ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        RoleService.ADMIN_ROLE + " is missing; migration 011 should have seeded it."));

        User user = userRepository.findByEmail(email).orElse(null);
        boolean existing = user != null;

        if (existing) {
            user.setEnabled(true);
            user.addRole(adminRole);
        } else {
            user = new User(email, passwordEncoder.encode(password));
            user.setEnabled(true);
            user.addRole(adminRole);
            roleRepository.findByName("ROLE_USER").ifPresent(user::addRole);
        }

        User saved = userRepository.save(user);

        Path secretsDir = resolvePortalHome().resolve("secrets");
        Path passwordFile = secretsDir.resolve("initialAdminPassword");
        try {
            Files.createDirectories(secretsDir);
            Files.writeString(passwordFile, password, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.setPosixFilePermissions(passwordFile, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows filesystem does not support POSIX permissions
            }
        } catch (Exception e) {
            log.warn("Could not write initial admin password file: {}", e.getMessage());
        }

        auditService.recordSystemAction(
                AuditAction.ADMIN_BOOTSTRAPPED,
                saved.getId(),
                saved.getEmail(),
                existing ? "autoGenerated=true;grantedAdminToExistingAccount=true" : "autoGenerated=true;createdNewAccount=true");

        String banner = """

                *************************************************************
                *************************************************************
                Portal SSO initial setup is required. An admin user has been created:

                  Username: %s
                  Password: %s

                This password has also been written to:
                  %s

                Please sign in at %s to complete setup.
                *************************************************************
                *************************************************************
                """.formatted(email, password, passwordFile.toAbsolutePath(), issuerUrl);

        System.out.println(banner);
        log.info(banner);
    }

    private boolean hasEnabledAdministrator() {
        return userRepository.countEnabledUsersWithRole(RoleService.ADMIN_ROLE) > 0;
    }
}

