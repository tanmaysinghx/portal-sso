package com.tanmaysinghx.portalsso.application.service;

import com.tanmaysinghx.portalsso.application.entity.Application;
import com.tanmaysinghx.portalsso.application.entity.ApplicationAccessType;
import com.tanmaysinghx.portalsso.application.repository.AccessRequestRepository;
import com.tanmaysinghx.portalsso.application.repository.ApplicationRepository;
import com.tanmaysinghx.portalsso.application.web.dto.ApplicationResponse;
import com.tanmaysinghx.portalsso.application.web.dto.CreateApplicationRequest;
import com.tanmaysinghx.portalsso.application.web.dto.UpdateApplicationRequest;
import com.tanmaysinghx.portalsso.application.web.dto.UserApplicationResponse;
import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.ResourceNotFoundException;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final AccessRequestRepository accessRequestRepository;
    private final UserRepository userRepository;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            RoleRepository roleRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder,
            AccessRequestRepository accessRequestRepository,
            UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.accessRequestRepository = accessRequestRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listAdmin(String search, String category, Boolean enabled) {
        List<Application> apps = applicationRepository.findAllWithRoles();

        return apps.stream()
                .filter(app -> {
                    if (search != null && !search.isBlank()) {
                        String s = search.toLowerCase();
                        boolean nameMatch = app.getName().toLowerCase().contains(s);
                        boolean descMatch = app.getDescription() != null && app.getDescription().toLowerCase().contains(s);
                        if (!nameMatch && !descMatch) {
                            return false;
                        }
                    }
                    if (category != null && !category.isBlank() && !category.equalsIgnoreCase("all")) {
                        if (!category.equalsIgnoreCase(app.getCategory())) {
                            return false;
                        }
                    }
                    if (enabled != null && app.isEnabled() != enabled) {
                        return false;
                    }
                    return true;
                })
                .map(ApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getById(UUID id) {
        Application app = applicationRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found: " + id));
        return ApplicationResponse.from(app);
    }

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest request) {
        Application app = new Application(request.name().trim(), request.appUrl().trim());
        app.setDescription(request.description() != null ? request.description().trim() : null);
        app.setIconUrl(request.iconUrl() != null && !request.iconUrl().isBlank() ? request.iconUrl().trim() : null);
        app.setCategory(request.category() != null && !request.category().isBlank() ? request.category().trim() : "General");
        app.setClientId(request.clientId() != null && !request.clientId().isBlank() ? request.clientId().trim() : null);
        app.setAccessType(request.accessType() != null ? request.accessType() : ApplicationAccessType.ALL_USERS);
        app.setEnabled(request.enabled() != null ? request.enabled() : true);
        app.setExternalAuthEnabled(Boolean.TRUE.equals(request.externalAuthEnabled()));
        app.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);

        if (request.externalRegistrationEnabled() != null) {
            app.setExternalRegistrationEnabled(request.externalRegistrationEnabled());
        }

        if (app.getAccessType() == ApplicationAccessType.RESTRICTED && request.roleIds() != null && !request.roleIds().isEmpty()) {
            List<Role> roles = roleRepository.findAllById(request.roleIds());
            app.setRoles(new HashSet<>(roles));
        } else {
            app.setRoles(new HashSet<>());
        }

        Application saved = applicationRepository.save(app);
        auditService.record(
                AuditAction.APPLICATION_CREATED,
                saved.getId(),
                saved.getName(),
                "category=%s, accessType=%s, enabled=%s".formatted(saved.getCategory(), saved.getAccessType(), saved.isEnabled()));

        return ApplicationResponse.from(saved);
    }

    @Transactional
    public ApplicationResponse update(UUID id, UpdateApplicationRequest request) {
        Application app = applicationRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found: " + id));

        app.setName(request.name().trim());
        app.setAppUrl(request.appUrl().trim());
        app.setDescription(request.description() != null ? request.description().trim() : null);
        app.setIconUrl(request.iconUrl() != null && !request.iconUrl().isBlank() ? request.iconUrl().trim() : null);
        app.setCategory(request.category() != null && !request.category().isBlank() ? request.category().trim() : "General");
        app.setClientId(request.clientId() != null && !request.clientId().isBlank() ? request.clientId().trim() : null);
        app.setAccessType(request.accessType() != null ? request.accessType() : ApplicationAccessType.ALL_USERS);
        app.setEnabled(request.enabled() != null ? request.enabled() : true);
        app.setExternalAuthEnabled(Boolean.TRUE.equals(request.externalAuthEnabled()));
        app.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);

        if (request.externalRegistrationEnabled() != null) {
            app.setExternalRegistrationEnabled(request.externalRegistrationEnabled());
        }

        if (app.getAccessType() == ApplicationAccessType.RESTRICTED && request.roleIds() != null) {
            List<Role> roles = roleRepository.findAllById(request.roleIds());
            app.setRoles(new HashSet<>(roles));
        } else if (app.getAccessType() == ApplicationAccessType.ALL_USERS) {
            app.getRoles().clear();
        }

        Application saved = applicationRepository.save(app);
        auditService.record(
                AuditAction.APPLICATION_UPDATED,
                saved.getId(),
                saved.getName(),
                "category=%s, accessType=%s, enabled=%s".formatted(saved.getCategory(), saved.getAccessType(), saved.isEnabled()));

        return ApplicationResponse.from(saved);
    }

    @Transactional
    public String rotateExternalAuthApiKey(UUID id) {
        Application app = applicationRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found: " + id));

        String rawKey = generateApiKey();
        app.setExternalAuthEnabled(true);
        app.setExternalAuthApiKeyHash(passwordEncoder.encode(rawKey));
        app.setExternalAuthApiKeyPrefix(rawKey.substring(0, 12));

        Application saved = applicationRepository.save(app);
        auditService.record(
                AuditAction.APPLICATION_UPDATED,
                saved.getId(),
                saved.getName(),
                "externalAuthApiKey=rotated, externalAuthEnabled=true, keyPrefix=%s".formatted(saved.getExternalAuthApiKeyPrefix()));

        return rawKey;
    }

    private static String generateApiKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "psso_app_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public void delete(UUID id) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found: " + id));

        applicationRepository.delete(app);
        auditService.record(
                AuditAction.APPLICATION_DELETED,
                app.getId(),
                app.getName(),
                "appUrl=%s, category=%s".formatted(app.getAppUrl(), app.getCategory()));
    }

    @Transactional(readOnly = true)
    public List<UserApplicationResponse> getUserApplications(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return List.of();
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        List<Application> allApps = applicationRepository.findAllEnabledWithRoles();

        Set<String> userRoleNames = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        Set<UUID> pendingAppIds = Set.of();
        if (!isAdmin) {
            var userOpt = userRepository.findByEmail(auth.getName());
            if (userOpt.isPresent()) {
                pendingAppIds = accessRequestRepository.findPendingByUserId(userOpt.get().getId()).stream()
                        .map(ar -> ar.getApplication().getId())
                        .collect(Collectors.toSet());
            }
        }

        Set<UUID> finalPendingAppIds = pendingAppIds;
        return allApps.stream()
                .map(app -> {
                    boolean accessible;
                    if (isAdmin) {
                        accessible = true;
                    } else if (app.getAccessType() == ApplicationAccessType.ALL_USERS) {
                        accessible = true;
                    } else {
                        accessible = app.getRoles().stream()
                                .anyMatch(r -> userRoleNames.contains(r.getName()));
                    }
                    String pendingStatus = finalPendingAppIds.contains(app.getId()) ? "PENDING" : null;
                    return UserApplicationResponse.from(app, accessible, pendingStatus);
                })
                .toList();
    }
}
