package com.tanmaysinghx.portalsso.bootstrap;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.service.RoleService;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <h2>Why configuration rather than a generated password</h2>
 *
 * <p>The alternatives were a random password printed to the log, or an unauthenticated {@code
 * /setup} page. Logging a live credential puts it into a stream that is routinely shipped
 * off-host to aggregators, and an unauthenticated setup route on an identity server is the
 * riskiest possible thing to get subtly wrong. Taking the credentials from configuration invents
 * no secret, adds no endpoint, and puts the password in the same place as the database password
 * it already sits beside.
 *
 * <h2>Guarantees</h2>
 *
 * <ul>
 *   <li><strong>Never a default.</strong> Both properties unset means this does nothing at all, so
 *       the product cannot ship with a known administrator.
 *   <li><strong>Idempotent.</strong> It acts only when no <em>enabled</em> administrator exists, so
 *       leaving the configuration in place does not recreate or overwrite anything on restart.
 *   <li><strong>Never overwrites a password.</strong> If the address already has an account, the
 *       role is granted and the account enabled, but the existing password stands — otherwise
 *       anyone editing configuration could silently reset a colleague's credentials.
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

    public AdminBootstrapper(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            BootstrapProperties properties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hasEnabledAdministrator()) {
            return;
        }

        if (!properties.isConfigured()) {
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

        if (password.length() < MIN_PASSWORD_LENGTH) {
            // Refused rather than accepted-with-a-warning: a warning scrolls past, and the result
            // would be the most privileged account on the server behind a weak password.
            log.error(
                    "Refusing to bootstrap administrator '{}': app.bootstrap.admin-password must be at "
                            + "least {} characters. No account was created.",
                    email,
                    MIN_PASSWORD_LENGTH);
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

    private boolean hasEnabledAdministrator() {
        return userRepository.countEnabledUsersWithRole(RoleService.ADMIN_ROLE) > 0;
    }
}
