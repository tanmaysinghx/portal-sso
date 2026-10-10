package com.tanmaysinghx.portalsso.application.service;

import com.tanmaysinghx.portalsso.application.entity.AccessRequest;
import com.tanmaysinghx.portalsso.application.entity.AccessRequestStatus;
import com.tanmaysinghx.portalsso.application.entity.Application;
import com.tanmaysinghx.portalsso.application.entity.ApplicationAccessType;
import com.tanmaysinghx.portalsso.application.repository.AccessRequestRepository;
import com.tanmaysinghx.portalsso.application.repository.ApplicationRepository;
import com.tanmaysinghx.portalsso.application.web.dto.AccessRequestResponse;
import com.tanmaysinghx.portalsso.application.web.dto.AdminSummaryResponse;
import com.tanmaysinghx.portalsso.application.web.dto.SubmitAccessRequestRequest;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.ResourceConflictException;
import com.tanmaysinghx.portalsso.common.error.ResourceNotFoundException;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccessRequestService {

    private final AccessRequestRepository accessRequestRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;

    public AccessRequestService(
            AccessRequestRepository accessRequestRepository,
            ApplicationRepository applicationRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuditService auditService) {
        this.accessRequestRepository = accessRequestRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AccessRequestResponse submitRequest(String requesterEmail, SubmitAccessRequestRequest request) {
        User user = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));

        Application app = applicationRepository.findByIdWithRoles(request.applicationId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found"));

        if (!app.isEnabled()) {
            throw new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found");
        }

        if (app.getAccessType() == ApplicationAccessType.ALL_USERS) {
            throw new BusinessRuleViolationException(ErrorCode.ACCESS_ALREADY_GRANTED, "This application is open to all users.");
        }

        boolean hasAccess = app.getRoles().stream()
                .anyMatch(role -> user.getRoles().stream()
                        .anyMatch(userRole -> userRole.getName().equals(role.getName())));

        if (hasAccess) {
            throw new BusinessRuleViolationException(ErrorCode.ACCESS_ALREADY_GRANTED, "User already has access to this application.");
        }

        if (accessRequestRepository.existsByUserIdAndApplicationIdAndStatus(user.getId(), app.getId(), AccessRequestStatus.PENDING)) {
            throw new ResourceConflictException(ErrorCode.ACCESS_REQUEST_DUPLICATE, "A pending access request already exists.");
        }

        User reviewer = userRepository.findByIdWithRoles(request.reviewerId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "Reviewer not found"));

        boolean isReviewerAdmin = reviewer.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_ADMIN"));

        if (!isReviewerAdmin) {
            throw new BusinessRuleViolationException(ErrorCode.ACCESS_DENIED, "Selected reviewer is not an administrator.");
        }

        AccessRequest ar = new AccessRequest(user, app, reviewer);
        ar.setReason(request.reason());
        AccessRequest saved = accessRequestRepository.save(ar);

        auditService.record(
                AuditAction.ACCESS_REQUEST_SUBMITTED,
                app.getId(),
                app.getName(),
                "requestedBy=%s, reviewer=%s".formatted(user.getEmail(), reviewer.getEmail()));

        return AccessRequestResponse.from(saved);
    }

    @Transactional
    public AccessRequestResponse approve(UUID requestId, String adminEmail, String comment) {
        AccessRequest ar = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ACCESS_REQUEST_NOT_FOUND, "Access request not found"));

        if (ar.getStatus() != AccessRequestStatus.PENDING) {
            throw new BusinessRuleViolationException(ErrorCode.MALFORMED_REQUEST, "Access request is already resolved.");
        }

        Application app = applicationRepository.findByIdWithRoles(ar.getApplication().getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found"));

        User user = ar.getUser();
        List<String> addedRoles = app.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        for (Role r : app.getRoles()) {
            user.addRole(r);
        }
        userRepository.save(user);

        ar.setStatus(AccessRequestStatus.APPROVED);
        ar.setReviewerComment(comment);
        ar.setResolvedAt(Instant.now());
        accessRequestRepository.save(ar);

        auditService.record(
                AuditAction.ACCESS_REQUEST_APPROVED,
                app.getId(),
                app.getName(),
                "approvedFor=%s, approvedBy=%s".formatted(user.getEmail(), adminEmail));

        auditService.record(
                AuditAction.USER_ROLES_CHANGED,
                user.getId(),
                user.getEmail(),
                "roles added: " + String.join(", ", addedRoles));

        return AccessRequestResponse.from(ar);
    }

    @Transactional
    public AccessRequestResponse deny(UUID requestId, String adminEmail, String comment) {
        AccessRequest ar = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ACCESS_REQUEST_NOT_FOUND, "Access request not found"));

        if (ar.getStatus() != AccessRequestStatus.PENDING) {
            throw new BusinessRuleViolationException(ErrorCode.MALFORMED_REQUEST, "Access request is already resolved.");
        }

        ar.setStatus(AccessRequestStatus.DENIED);
        ar.setReviewerComment(comment);
        ar.setResolvedAt(Instant.now());
        accessRequestRepository.save(ar);

        auditService.record(
                AuditAction.ACCESS_REQUEST_DENIED,
                ar.getApplication().getId(),
                ar.getApplication().getName(),
                "deniedFor=%s, deniedBy=%s".formatted(ar.getUser().getEmail(), adminEmail));

        return AccessRequestResponse.from(ar);
    }

    @Transactional(readOnly = true)
    public List<AccessRequestResponse> listForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        return accessRequestRepository.findByUserIdWithDetails(user.getId()).stream()
                .map(AccessRequestResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccessRequestResponse> listAll(AccessRequestStatus status) {
        return accessRequestRepository.findAllWithDetails(status).stream()
                .map(AccessRequestResponse::from)
                .collect(Collectors.toList());
    }

    public long countPending() {
        return accessRequestRepository.countByStatus(AccessRequestStatus.PENDING);
    }

    public List<AdminSummaryResponse> listAdmins() {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElse(null);
        if (adminRole == null) {
            return List.of();
        }
        return userRepository.findEnabledUsersByRoleName("ROLE_ADMIN").stream()
                .map(u -> new AdminSummaryResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName()))
                .collect(Collectors.toList());
    }
}
