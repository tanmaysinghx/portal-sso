package com.tanmaysinghx.portalsso.user.service;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.ResourceConflictException;
import com.tanmaysinghx.portalsso.common.error.ResourceNotFoundException;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.web.dto.CreateRoleRequest;
import com.tanmaysinghx.portalsso.user.web.dto.RoleResponse;
import com.tanmaysinghx.portalsso.user.web.dto.UpdateRoleRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role administration, and the invariants that stop it locking everyone out.
 *
 * <p>The dangerous property of this feature is that every operation is one click away from making
 * the server unadministrable: {@code user_roles} has {@code ON DELETE CASCADE}, so deleting
 * {@code ROLE_ADMIN} would strip every administrator at once, and demoting the wrong account has
 * the same effect one user at a time. The guards below are the point of this class, which is why
 * they live here rather than in the controller.
 */
@Service
public class RoleService {

    /**
     * Roles the application itself names. {@code ROLE_ADMIN} appears in
     * {@code @PreAuthorize("hasRole('ADMIN')")} on every admin controller and {@code ROLE_USER} is
     * the default for self-registration, so neither can be deleted without breaking the product.
     */
    public static final Set<String> PROTECTED_ROLES = Set.of("ROLE_ADMIN", "ROLE_USER");
    public static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public RoleService(RoleRepository roleRepository, UserRepository userRepository, AuditService auditService) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : roleRepository.countUsersPerRole()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return roleRepository.findAllByOrderByNameAsc().stream()
                .map(role -> RoleResponse.from(
                        role, counts.getOrDefault(role.getId(), 0L), PROTECTED_ROLES.contains(role.getName())))
                .toList();
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest request) {
        String name = request.name().trim();
        if (roleRepository.existsByName(name)) {
            throw new ResourceConflictException(ErrorCode.ROLE_ALREADY_EXISTS, "A role named " + name + " already exists.");
        }

        Role saved = roleRepository.save(new Role(name, blankToNull(request.description())));
        auditService.record(AuditAction.ROLE_CREATED, saved.getId(), saved.getName(), null);
        return RoleResponse.from(saved, 0L, PROTECTED_ROLES.contains(saved.getName()));
    }

    @Transactional
    public RoleResponse update(UUID id, UpdateRoleRequest request) {
        Role role = require(id);
        String before = role.getDescription();

        role.setDescription(blankToNull(request.description()));
        Role saved = roleRepository.save(role);

        auditService.record(
                AuditAction.ROLE_UPDATED,
                saved.getId(),
                saved.getName(),
                "description: %s -> %s".formatted(before, saved.getDescription()));

        return RoleResponse.from(saved, userCount(saved.getId()), PROTECTED_ROLES.contains(saved.getName()));
    }

    /**
     * Deleting a role strips it from everyone who holds it. That is allowed for a role an operator
     * defined, and refused for the platform roles — {@code user_roles} cascades on delete, so
     * removing {@code ROLE_ADMIN} would silently demote every administrator in a single statement
     * and leave nobody able to sign in and undo it.
     *
     * <p>The association is cleared through JPA rather than left to the database cascade: the
     * cascade would do it, but only the explicit path can count the users affected, and that count
     * is the most useful thing the audit entry can carry.
     */
    @Transactional
    public void delete(UUID id) {
        Role role = require(id);
        if (PROTECTED_ROLES.contains(role.getName())) {
            throw new BusinessRuleViolationException(
                    ErrorCode.ROLE_PROTECTED,
                    role.getName() + " is required by the platform and cannot be deleted.");
        }

        List<User> holders = userRepository.findAllByRoleId(id);
        for (User user : holders) {
            user.removeRole(role);
        }
        userRepository.saveAll(holders);
        roleRepository.delete(role);

        auditService.record(
                AuditAction.ROLE_DELETED, role.getId(), role.getName(), "removedFromUsers=" + holders.size());
    }

    /**
     * Replaces a user's roles wholesale.
     *
     * <p>Two refusals here, both about not being able to undo the change afterwards:
     *
     * <ul>
     *   <li><strong>You cannot demote yourself.</strong> Same reasoning as the existing
     *       self-disable rule — it is the likeliest accident, and the moment it succeeds you no
     *       longer have the access needed to reverse it.
     *   <li><strong>You cannot remove the last administrator.</strong> Defence in depth: the
     *       self-check makes this hard to reach today, but that argument depends on the other
     *       guards staying exactly as they are, and the consequence is an unadministrable server.
     * </ul>
     */
    @Transactional
    public User setUserRoles(UUID userId, Set<String> roleNames, String actingUserEmail) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "No user found with ID: " + userId));

        Set<String> before = new TreeSet<>(user.getRoles().stream().map(Role::getName).toList());
        Set<String> after = new TreeSet<>(roleNames);

        if (before.contains(ADMIN_ROLE) && !after.contains(ADMIN_ROLE)) {
            if (user.getEmail().equals(actingUserEmail)) {
                throw new BusinessRuleViolationException(
                        ErrorCode.SELF_DEMOTION_PROHIBITED, "You can't remove your own administrator role.");
            }
            if (userRepository.countOtherEnabledUsersWithRole(ADMIN_ROLE, userId) == 0) {
                throw new BusinessRuleViolationException(
                        ErrorCode.LAST_ADMIN_PROHIBITED,
                        "%s is the only enabled administrator; grant the role to someone else first."
                                .formatted(user.getEmail()));
            }
        }

        // Resolved before mutating so an unknown name fails the whole request rather than leaving
        // the user with a half-applied set.
        Set<Role> resolved = new java.util.LinkedHashSet<>();
        for (String name : after) {
            resolved.add(roleRepository.findByName(name)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, "Role not found: " + name)));
        }

        user.getRoles().clear();
        resolved.forEach(user::addRole);
        User saved = userRepository.save(user);

        if (!before.equals(after)) {
            auditService.record(
                    AuditAction.USER_ROLES_CHANGED,
                    saved.getId(),
                    saved.getEmail(),
                    "roles: %s -> %s".formatted(String.join(" ", before), String.join(" ", after)));
        }
        return saved;
    }

    private Role require(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, "No role found with ID: " + id));
    }

    private long userCount(UUID roleId) {
        return roleRepository.countUsersPerRole().stream()
                .filter(row -> roleId.equals(row[0]))
                .map(row -> (Long) row[1])
                .findFirst()
                .orElse(0L);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
