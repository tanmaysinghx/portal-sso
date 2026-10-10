package com.tanmaysinghx.portalsso.application.service;

import com.tanmaysinghx.portalsso.application.entity.Application;
import com.tanmaysinghx.portalsso.application.repository.ApplicationRepository;
import com.tanmaysinghx.portalsso.application.web.dto.ExternalAppAuthResponse;
import com.tanmaysinghx.portalsso.application.web.dto.ExternalAppLoginRequest;
import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.ResourceConflictException;
import com.tanmaysinghx.portalsso.common.error.ResourceNotFoundException;
import com.tanmaysinghx.portalsso.registration.config.RegistrationProperties;
import com.tanmaysinghx.portalsso.registration.web.dto.RegisterRequest;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalApplicationAuthService {

    public static final String API_KEY_HEADER = "X-Portal-App-Key";

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationProperties registrationProperties;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;

    public ExternalApplicationAuthService(
            ApplicationRepository applicationRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            RegistrationProperties registrationProperties,
            PasswordPolicy passwordPolicy,
            AuditService auditService) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.registrationProperties = registrationProperties;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
    }

    @Transactional
    public ExternalAppAuthResponse register(UUID applicationId, String apiKey, RegisterRequest request) {
        Application app = requireExternalAuthApp(applicationId, apiKey);
        if (!app.isExternalRegistrationEnabled()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.EXTERNAL_REGISTRATION_DISABLED,
                    "Registration is not enabled for this application.");
        }
        passwordPolicy.validate(request.password());

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ResourceConflictException(
                    ErrorCode.USER_ALREADY_EXISTS, "An account with that email already exists.");
        }

        Role defaultRole = roleRepository
                .findByName(registrationProperties.defaultRole())
                .orElseGet(() -> roleRepository.save(new Role(registrationProperties.defaultRole(), "Externally registered user")));

        User user = new User(email, passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(!registrationProperties.requireAdminApproval());
        user.addRole(defaultRole);

        if (app.getRoles() != null) {
            app.getRoles().forEach(user::addRole);
        }

        User saved = userRepository.save(user);
        auditService.record(
                AuditAction.USER_SELF_REGISTERED,
                saved.getId(),
                email,
                "sourceApplication=%s, pendingApproval=%s".formatted(app.getName(), registrationProperties.requireAdminApproval()));

        return new ExternalAppAuthResponse(
                saved.isEnabled(),
                false,
                saved.isEnabled() ? userSummary(saved) : null);
    }

    @Transactional
    public ExternalAppAuthResponse login(UUID applicationId, String apiKey, ExternalAppLoginRequest request) {
        Application app = requireExternalAuthApp(applicationId, apiKey);
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> invalidCredentials(app, email));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials(app, email);
        }
        if (!user.isEnabled()) {
            throw new BusinessRuleViolationException(ErrorCode.ACCOUNT_DISABLED, "The user account is disabled.");
        }
        if (user.isAccountLocked()) {
            throw new BusinessRuleViolationException(ErrorCode.ACCOUNT_LOCKED, "The user account is locked.");
        }

        auditService.record(
                AuditAction.USER_LOGIN_SUCCESS,
                user.getId(),
                user.getEmail(),
                "sourceApplication=%s, externalApi=true".formatted(app.getName()));

        return new ExternalAppAuthResponse(true, user.isMfaEnabled(), userSummary(user));
    }

    private Application requireExternalAuthApp(UUID applicationId, String apiKey) {
        Application app = applicationRepository.findByIdWithRoles(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found: " + applicationId));
        if (!app.isEnabled() || !app.isExternalAuthEnabled() || app.getExternalAuthApiKeyHash() == null) {
            throw new BusinessRuleViolationException(ErrorCode.ACCESS_DENIED, "External authentication is not enabled for this application.");
        }
        if (apiKey == null || apiKey.isBlank() || !passwordEncoder.matches(apiKey, app.getExternalAuthApiKeyHash())) {
            throw new BusinessRuleViolationException(ErrorCode.UNAUTHORIZED, "Invalid application API key.");
        }
        return app;
    }

    private BusinessRuleViolationException invalidCredentials(Application app, String email) {
        auditService.record(
                AuditAction.USER_LOGIN_FAILED,
                (UUID) null,
                email,
                "sourceApplication=%s, externalApi=true".formatted(app.getName()));
        return new BusinessRuleViolationException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static ExternalAppAuthResponse.UserSummary userSummary(User user) {
        return new ExternalAppAuthResponse.UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRoles().stream()
                        .map(Role::getName)
                        .sorted(Comparator.naturalOrder())
                        .toList());
    }

}
