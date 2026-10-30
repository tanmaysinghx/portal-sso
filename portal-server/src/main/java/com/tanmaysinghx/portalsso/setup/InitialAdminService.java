package com.tanmaysinghx.portalsso.setup;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.PortalException;
import com.tanmaysinghx.portalsso.common.error.ResourceConflictException;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy.PasswordPolicyDescription;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.service.RoleService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first administrator from the setup wizard. */
@Service
public class InitialAdminService {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminService.class);

    /** Same floor as {@code AdminBootstrapper}: the most privileged account on the server. */
    static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;

    public InitialAdminService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
    }

    public boolean isSetupRequired() {
        return userRepository.countEnabledUsersWithRole(RoleService.ADMIN_ROLE) == 0;
    }

    /** The configured policy with this account's higher length floor, for the wizard's checklist. */
    public PasswordPolicyDescription adminPasswordPolicy() {
        PasswordPolicyDescription p = passwordPolicy.describe();
        return new PasswordPolicyDescription(
                Math.max(p.minLength(), MIN_ADMIN_PASSWORD_LENGTH),
                p.maxLength(),
                p.requireUppercase(),
                p.requireLowercase(),
                p.requireDigit(),
                p.requireSymbol(),
                p.maxAgeDays(),
                p.expiryWarningDays());
    }

    /** Checks everything that can be checked without spending the setup key. */
    public void validate(String email, String password) {
        if (!isSetupRequired()) {
            throw new PortalException(ErrorCode.SETUP_ALREADY_COMPLETED);
        }
        List<String> failures = new ArrayList<>(passwordPolicy.failures(password));
        if (password.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            failures.add("must be at least " + MIN_ADMIN_PASSWORD_LENGTH + " characters for the administrator account");
        }
        if (!failures.isEmpty()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.WEAK_PASSWORD, "Password " + String.join("; ", failures) + ".");
        }
        // Never adopt an existing account: setting its password from an unauthenticated page would
        // be a takeover of whoever owns it. Recovering a disabled admin is what app.bootstrap.* is for.
        if (userRepository.findByEmail(normalize(email)).isPresent()) {
            throw new ResourceConflictException(
                    ErrorCode.USER_ALREADY_EXISTS,
                    "An account with this email already exists. Choose a different address for the administrator.");
        }
    }

    @Transactional
    public User createAdmin(String email, String password, String firstName, String lastName) {
        validate(email, password);

        Role adminRole = roleRepository.findByName(RoleService.ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        RoleService.ADMIN_ROLE + " is missing; migration 011 should have seeded it."));

        User user = new User(normalize(email), passwordEncoder.encode(password));
        if (firstName != null && !firstName.isBlank()) {
            user.setFirstName(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            user.setLastName(lastName.trim());
        }
        user.setEnabled(true);
        user.addRole(adminRole);
        roleRepository.findByName("ROLE_USER").ifPresent(user::addRole);

        User saved = userRepository.save(user);
        auditService.recordSystemAction(
                AuditAction.ADMIN_BOOTSTRAPPED, saved.getId(), saved.getEmail(), "setupWizard=true");
        log.info("Initial administrator '{}' created via the setup wizard.", saved.getEmail());
        return saved;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
