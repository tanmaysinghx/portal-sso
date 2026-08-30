package com.tanmaysinghx.portalsso.user.web;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.common.api.PageResponse;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.ResourceConflictException;
import com.tanmaysinghx.portalsso.common.error.ResourceNotFoundException;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.service.RoleService;
import com.tanmaysinghx.portalsso.user.service.UserQueryService;
import com.tanmaysinghx.portalsso.user.web.dto.CreateUserRequest;
import com.tanmaysinghx.portalsso.user.web.dto.SetUserEnabledRequest;
import com.tanmaysinghx.portalsso.user.web.dto.SetUserRolesRequest;
import com.tanmaysinghx.portalsso.user.web.dto.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.tanmaysinghx.portalsso.security.mfa.MfaService mfaService;
    private final AuditService auditService;
    private final RoleService roleService;
    private final UserQueryService userQueryService;
    private final PasswordPolicy passwordPolicy;

    public AdminUserController(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            com.tanmaysinghx.portalsso.security.mfa.MfaService mfaService,
            AuditService auditService,
            RoleService roleService,
            UserQueryService userQueryService,
            PasswordPolicy passwordPolicy) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.mfaService = mfaService;
        this.auditService = auditService;
        this.roleService = roleService;
        this.userQueryService = userQueryService;
        this.passwordPolicy = passwordPolicy;
    }

    /**
     * Paged, searchable and filterable. This used to return every user in one array, which was fine
     * at three accounts and a full table scan plus a full render at a few thousand.
     */
    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        return userQueryService.find(search, enabled, role, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceConflictException(
                    ErrorCode.USER_ALREADY_EXISTS, "A user with email already exists: " + request.email());
        }

        // Checked here rather than as a DTO annotation so the rules stay configurable and identical
        // across every path that sets a password.
        passwordPolicy.validate(request.password());

        User user = new User(request.email(), passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(request.enabled() != null ? request.enabled() : true);

        Set<String> roleNames = (request.roles() == null || request.roles().isEmpty())
                ? Set.of("ROLE_USER")
                : request.roles();

        for (String roleName : roleNames) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            ErrorCode.RESOURCE_NOT_FOUND, "Role not found: " + roleName));
            user.addRole(role);
        }

        User saved = userRepository.save(user);

        // Roles and the enabled flag are recorded because they are the parts of this call that
        // grant access. The password is deliberately absent — not even its length.
        auditService.record(
                AuditAction.USER_CREATED,
                saved.getId(),
                saved.getEmail(),
                "roles=%s, enabled=%s".formatted(String.join(" ", roleNames), saved.isEnabled()));

        return UserResponse.from(saved);
    }

    /**
     * {@code @Transactional} so the save and the roles-touching DTO mapping share one session —
     * {@link UserRepository#findById} doesn't eager-fetch roles, and the LazyInitializationException
     * that follows without this has already bitten this codebase once (see JwtClaimsCustomizerConfig).
     */
    @PatchMapping("/{id}")
    @Transactional
    public UserResponse setEnabled(
            @PathVariable UUID id, @Valid @RequestBody SetUserEnabledRequest request, Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "No user found with ID: " + id));

        if (!request.enabled() && user.getEmail().equals(authentication.getName())) {
            throw new BusinessRuleViolationException(ErrorCode.SELF_DISABLE_PROHIBITED, "You can't disable your own account.");
        }

        user.setEnabled(request.enabled());
        User saved = userRepository.save(user);

        // Two actions rather than one "USER_ENABLED_CHANGED" with a flag: locking an account out is
        // the event someone investigating an outage searches for, and it should be one filter away.
        auditService.record(
                request.enabled() ? AuditAction.USER_ENABLED : AuditAction.USER_DISABLED,
                saved.getId(),
                saved.getEmail(),
                null);

        return UserResponse.from(saved);
    }

    /**
     * Replaces a user's roles. This is the endpoint that makes a second administrator possible:
     * roles could previously only be set at creation time, so an existing account could never be
     * promoted or demoted through the product.
     *
     * <p>The guards that stop this locking everyone out live in {@link RoleService#setUserRoles} —
     * the acting user's email is passed down because the self-demotion check needs it.
     */
    @PutMapping("/{id}/roles")
    // Same reason as setEnabled above: the response mapping reads user.getRoles(), which needs the
    // session still open.
    @Transactional
    public UserResponse setRoles(
            @PathVariable UUID id, @Valid @RequestBody SetUserRolesRequest request, Authentication authentication) {
        return UserResponse.from(roleService.setUserRoles(id, request.roles(), authentication.getName()));
    }

    /**
     * Clears a lockout applied by {@link com.tanmaysinghx.portalsso.security.LoginAttemptListener}
     * after too many failed sign-ins. Without this an administrator's only recovery path would be
     * editing the database by hand, so the lockout feature is not safe to ship without it.
     *
     * <p>Resets the counter as well as the flag — leaving it at the threshold would re-lock the
     * account on the very next mistyped password.
     */
    @PostMapping("/{id}/unlock")
    @Transactional
    public UserResponse unlock(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "No user found with ID: " + id));

        int clearedAttempts = user.getFailedLoginAttempts();
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        User saved = userRepository.save(user);

        auditService.record(
                AuditAction.USER_UNLOCKED,
                saved.getId(),
                saved.getEmail(),
                "clearedFailedAttempts=" + clearedAttempts);

        return UserResponse.from(saved);
    }

    /**
     * Resets/disables Multi-Factor Authentication for a user who has lost their device.
     */
    @PostMapping("/{id}/mfa/reset")
    @Transactional
    public UserResponse resetMfa(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "No user found with ID: " + id));

        mfaService.adminResetMfa(user);
        auditService.record(AuditAction.USER_MFA_RESET, user.getId(), user.getEmail(), null);
        return UserResponse.from(user);
    }
}
