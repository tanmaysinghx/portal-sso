package com.tanmaysinghx.portalsso.bootstrap;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.setup.SetupKeyService;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.service.RoleService;
import java.nio.file.Path;
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
 * Makes sure a deployment with no administrator has a way to get one.
 *
 * <p>Without this, a fresh deployment is unusable: Liquibase creates the schema and seeds the two
 * platform roles, but the {@code users} table is empty, {@code TestDataSeeder} is dev-only and off
 * by default, and self-registration only ever grants {@code ROLE_USER} — so the operator gets a
 * working server with no account to sign in to and no way to make one.
 *
 * <h2>Jenkins LTS-Style Setup Key vs Explicit Configuration</h2>
 *
 * <p>Operators can provide explicit credentials via {@code APP_BOOTSTRAP_ADMIN_EMAIL} and
 * {@code APP_BOOTSTRAP_ADMIN_PASSWORD}, and the account is created at startup. Without them, if
 * {@code autoGenerate} is enabled (the standalone default), no account is created: a one-time setup
 * key is printed to the console and saved to {@code ~/.portal-sso/secrets/initialAdminPassword},
 * and the operator creates their own administrator in the sign-in page's setup wizard.
 *
 * <h2>Guarantees</h2>
 *
 * <ul>
 *   <li><strong>Zero static credentials.</strong> Nothing is created with a password the operator
 *       did not choose.
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
    private final SetupKeyService setupKeyService;

    @Value("${spring.security.oauth2.authorizationserver.issuer:http://localhost:8080}")
    private String issuerUrl;

    public AdminBootstrapper(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            BootstrapProperties properties,
            PasswordPolicy passwordPolicy,
            SetupKeyService setupKeyService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.properties = properties;
        this.passwordPolicy = passwordPolicy;
        this.setupKeyService = setupKeyService;
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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hasEnabledAdministrator()) {
            return;
        }

        if (!properties.isConfigured()) {
            if (properties.shouldAutoGenerate()) {
                issueSetupKey();
                return;
            }
            // Worth shouting about: the server starts cleanly and answers requests, so without this
            // the operator's only symptom is a sign-in page that rejects every credential they try.
            log.warn("""
                    No enabled administrator exists and no bootstrap credentials are configured, \
                    so nobody can sign in to the admin console. Set app.bootstrap.admin-email and \
                    app.bootstrap.admin-password (APP_BOOTSTRAP_ADMIN_EMAIL / \
                    APP_BOOTSTRAP_ADMIN_PASSWORD), or app.bootstrap.auto-generate=true for a setup \
                    key, and restart.""");
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

    private void issueSetupKey() {
        String key = setupKeyService.issue();
        String banner = """

                *************************************************************
                *************************************************************
                Portal SSO initial setup is required.

                Open %s/sign-in and enter this setup key to create
                the first administrator:

                  %s

                The key has also been written to:
                  %s

                It works once. If the server restarts before setup is
                finished, a new key replaces it.
                *************************************************************
                *************************************************************
                """.formatted(issuerUrl, key, setupKeyService.keyFile());

        System.out.println(banner);
        log.info(banner);
    }

    private boolean hasEnabledAdministrator() {
        return userRepository.countEnabledUsersWithRole(RoleService.ADMIN_ROLE) > 0;
    }
}
