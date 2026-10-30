package com.tanmaysinghx.portalsso.setup.web;

import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.PortalException;
import com.tanmaysinghx.portalsso.database.dto.DatabaseStatusResponse;
import com.tanmaysinghx.portalsso.database.service.DatabaseMigrationService;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy.PasswordPolicyDescription;
import com.tanmaysinghx.portalsso.setup.InitialAdminService;
import com.tanmaysinghx.portalsso.setup.SetupKeyService;
import com.tanmaysinghx.portalsso.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The first-run setup wizard. Open only while the server has no enabled administrator, and only to
 * someone holding the setup key from {@link SetupKeyService} — this page is unauthenticated, so the
 * key is the only thing standing between a fresh install and whoever finds it first.
 */
@RestController
@RequestMapping("/api/public/setup")
public class SetupController {

    private final InitialAdminService initialAdminService;
    private final SetupKeyService setupKeyService;
    private final DatabaseMigrationService migrationService;

    public SetupController(
            InitialAdminService initialAdminService,
            SetupKeyService setupKeyService,
            DatabaseMigrationService migrationService) {
        this.initialAdminService = initialAdminService;
        this.setupKeyService = setupKeyService;
        this.migrationService = migrationService;
    }

    /**
     * @param setupKeyIssued false while setup is required means the wizard cannot be used: the
     *     operator disabled auto-generation and must configure {@code APP_BOOTSTRAP_ADMIN_*} instead
     * @param setupKeyFile where to find the key; only sent while setup is required
     */
    public record SetupStatusResponse(
            boolean setupRequired,
            boolean setupKeyIssued,
            String setupKeyFile,
            PasswordPolicyDescription passwordPolicy,
            String databaseType,
            boolean isEmbeddedDatabase,
            String databaseProduct) {}

    public record SetupInitializeRequest(
            @NotBlank(message = "Setup key is required") String setupKey,
            @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
            @NotBlank(message = "Password is required") String password,
            String firstName,
            String lastName) {}

    @GetMapping("/status")
    public ResponseEntity<SetupStatusResponse> getSetupStatus() {
        boolean setupRequired = initialAdminService.isSetupRequired();
        boolean keyIssued = setupRequired && setupKeyService.isIssued();
        DatabaseStatusResponse dbStatus = migrationService.getDatabaseStatus();
        return ResponseEntity.ok(new SetupStatusResponse(
                setupRequired,
                keyIssued,
                keyIssued ? setupKeyService.keyFile().toString() : null,
                initialAdminService.adminPasswordPolicy(),
                dbStatus.databaseType(),
                dbStatus.isEmbedded(),
                dbStatus.databaseProductName()));
    }

    @PostMapping("/initialize")
    public ResponseEntity<Map<String, Object>> initializeAdmin(
            @Valid @RequestBody SetupInitializeRequest request) {
        // Validated before the key is checked so a typo in the password does not cost the operator
        // their key, and after it again inside the transaction for the race.
        initialAdminService.validate(request.email(), request.password());

        if (!setupKeyService.consume(request.setupKey())) {
            throw new PortalException(ErrorCode.SETUP_KEY_INVALID);
        }

        User admin;
        try {
            admin = initialAdminService.createAdmin(
                    request.email(), request.password(), request.firstName(), request.lastName());
        } catch (RuntimeException e) {
            setupKeyService.restore(request.setupKey());
            throw e;
        }
        setupKeyService.complete();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Administrator account created.",
                "email", admin.getEmail()));
    }
}
