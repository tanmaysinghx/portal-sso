package com.tanmaysinghx.portalsso.setup.web;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.database.dto.DatabaseStatusResponse;
import com.tanmaysinghx.portalsso.database.service.DatabaseMigrationService;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.service.RoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/setup")
public class SetupController {

    private static final Logger log = LoggerFactory.getLogger(SetupController.class);
    private static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;
    private final DatabaseMigrationService migrationService;

    public SetupController(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            AuditService auditService,
            DatabaseMigrationService migrationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
        this.migrationService = migrationService;
    }

    public record SetupStatusResponse(
            boolean setupRequired,
            String databaseType,
            boolean isEmbeddedDatabase,
            String databaseProduct,
            String version) {}

    public record SetupInitializeRequest(
            @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
            @NotBlank(message = "Password is required") String password,
            String firstName,
            String lastName) {}

    @GetMapping("/status")
    public ResponseEntity<SetupStatusResponse> getSetupStatus() {
        boolean setupRequired = userRepository.countEnabledUsersWithRole(RoleService.ADMIN_ROLE) == 0;
        DatabaseStatusResponse dbStatus = migrationService.getDatabaseStatus();
        return ResponseEntity.ok(new SetupStatusResponse(
                setupRequired,
                dbStatus.databaseType(),
                dbStatus.isEmbedded(),
                dbStatus.databaseProductName(),
                "v25.0.8"));
    }

    @PostMapping("/initialize")
    @Transactional
    public ResponseEntity<Map<String, Object>> initializeAdmin(
            @Valid @RequestBody SetupInitializeRequest request) {
        if (userRepository.countEnabledUsersWithRole(RoleService.ADMIN_ROLE) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Initial setup has already been completed.");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String password = request.password();

        List<String> failures = new ArrayList<>(passwordPolicy.failures(password));
        if (password.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            failures.add("must be at least " + MIN_ADMIN_PASSWORD_LENGTH + " characters for the administrator account");
        }
        if (!failures.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Password does not meet requirements: " + String.join("; ", failures));
        }

        Role adminRole = roleRepository.findByName(RoleService.ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        RoleService.ADMIN_ROLE + " is missing from database."));
        Role userRole = roleRepository.findByName("ROLE_USER").orElse(null);

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = new User(email, passwordEncoder.encode(password));
        } else {
            user.setPasswordHash(passwordEncoder.encode(password));
        }

        if (request.firstName() != null && !request.firstName().isBlank()) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            user.setLastName(request.lastName().trim());
        }

        user.setEnabled(true);
        user.setAccountLocked(false);
        user.addRole(adminRole);
        if (userRole != null) {
            user.addRole(userRole);
        }

        User saved = userRepository.save(user);
        auditService.record(
                AuditAction.ADMIN_BOOTSTRAPPED,
                saved.getId().toString(),
                saved.getEmail(),
                "Initial administrator created via Web Setup Wizard");

        log.info("Initial administrator '{}' created successfully via Web Setup Wizard.", email);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Administrator account created successfully. You may now sign in.",
                "email", email));
    }
}
