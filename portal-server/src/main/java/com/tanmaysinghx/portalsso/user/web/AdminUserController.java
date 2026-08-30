package com.tanmaysinghx.portalsso.user.web;

import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.ResourceConflictException;
import com.tanmaysinghx.portalsso.common.error.ResourceNotFoundException;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.web.dto.CreateUserRequest;
import com.tanmaysinghx.portalsso.user.web.dto.SetUserEnabledRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userRepository.findAllWithRoles().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceConflictException(
                    ErrorCode.USER_ALREADY_EXISTS, "A user with email already exists: " + request.email());
        }

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
        return UserResponse.from(userRepository.save(user));
    }
}
