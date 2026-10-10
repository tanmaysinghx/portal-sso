package com.tanmaysinghx.portalsso.user.service;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.web.dto.BulkUserImportRequest;
import com.tanmaysinghx.portalsso.user.web.dto.BulkUserImportRequest.ImportUserItem;
import com.tanmaysinghx.portalsso.user.web.dto.BulkUserImportResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserImportService {

    private static final Logger log = LoggerFactory.getLogger(UserImportService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;

    public UserImportService(
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

    @Transactional
    public BulkUserImportResponse importUsers(BulkUserImportRequest request) {
        List<ImportUserItem> items = new ArrayList<>();

        if (request.users() != null && !request.users().isEmpty()) {
            items.addAll(request.users());
        }

        if (request.csvContent() != null && !request.csvContent().isBlank()) {
            items.addAll(parseCsv(request.csvContent()));
        }

        int total = items.size();
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        String fallbackPassword = (request.defaultPassword() != null && !request.defaultPassword().isBlank())
                ? request.defaultPassword()
                : "PortalSSO@" + UUID.randomUUID().toString().substring(0, 8) + "!";

        Set<String> defaultRoles = (request.defaultRoles() != null && !request.defaultRoles().isEmpty())
                ? request.defaultRoles()
                : Set.of("ROLE_USER");

        boolean defaultEnabled = request.defaultEnabled() == null || request.defaultEnabled();

        for (int i = 0; i < items.size(); i++) {
            ImportUserItem item = items.get(i);
            int rowNumber = i + 1;

            if (item.email() == null || item.email().isBlank() || !EMAIL_PATTERN.matcher(item.email().trim()).matches()) {
                errors.add("Row " + rowNumber + ": Invalid or missing email address (" + item.email() + ")");
                skipped++;
                continue;
            }

            String email = item.email().trim().toLowerCase(Locale.ROOT);

            if (userRepository.existsByEmail(email)) {
                errors.add("Row " + rowNumber + " (" + email + "): Account already exists in directory.");
                skipped++;
                continue;
            }

            String rawPassword = (item.password() != null && !item.password().isBlank())
                    ? item.password()
                    : fallbackPassword;

            try {
                passwordPolicy.validate(rawPassword);
            } catch (Exception ex) {
                errors.add("Row " + rowNumber + " (" + email + "): Password policy violation: " + ex.getMessage());
                skipped++;
                continue;
            }

            try {
                User user = new User(email, passwordEncoder.encode(rawPassword));
                user.setFirstName(item.firstName() != null ? item.firstName().trim() : null);
                user.setLastName(item.lastName() != null ? item.lastName().trim() : null);
                user.setEnabled(item.enabled() != null ? item.enabled() : defaultEnabled);

                Set<String> rolesToAssign = (item.roles() != null && !item.roles().isEmpty())
                        ? item.roles()
                        : defaultRoles;

                for (String roleName : rolesToAssign) {
                    String formattedName = roleName.trim();
                    if (!formattedName.startsWith("ROLE_")) {
                        formattedName = "ROLE_" + formattedName.toUpperCase(Locale.ROOT);
                    }
                    Role role = roleRepository.findByName(formattedName)
                            .orElseGet(() -> roleRepository.findByName("ROLE_USER").orElse(null));
                    if (role != null) {
                        user.addRole(role);
                    }
                }

                User saved = userRepository.save(user);

                auditService.record(
                        AuditAction.USER_CREATED,
                        saved.getId(),
                        saved.getEmail(),
                        "bulk_import=true, enabled=" + saved.isEnabled());

                imported++;
            } catch (Exception ex) {
                log.error("Failed to import user at row {}: {}", rowNumber, email, ex);
                errors.add("Row " + rowNumber + " (" + email + "): Internal error: " + ex.getMessage());
                skipped++;
            }
        }

        return new BulkUserImportResponse(total, imported, skipped, errors);
    }

    private List<ImportUserItem> parseCsv(String csvContent) {
        List<ImportUserItem> items = new ArrayList<>();
        String[] lines = csvContent.split("\\r?\\n");

        if (lines.length == 0) {
            return items;
        }

        int startIndex = 0;
        String firstLine = lines[0].toLowerCase(Locale.ROOT);
        if (firstLine.contains("email") || firstLine.contains("username") || firstLine.contains("first")) {
            startIndex = 1; // Skip header line
        }

        for (int i = startIndex; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Simple CSV tokens split handling basic quotes
            List<String> tokens = parseCsvLine(line);
            if (tokens.isEmpty()) {
                continue;
            }

            String email = tokens.get(0);
            String firstName = tokens.size() > 1 ? tokens.get(1) : "";
            String lastName = tokens.size() > 2 ? tokens.get(2) : "";
            String password = tokens.size() > 3 ? tokens.get(3) : "";

            Set<String> roles = new HashSet<>();
            if (tokens.size() > 4 && !tokens.get(4).isBlank()) {
                String rolesRaw = tokens.get(4);
                String[] split = rolesRaw.split("[;|]");
                roles.addAll(Arrays.stream(split).map(String::trim).filter(s -> !s.isEmpty()).toList());
            }

            Boolean enabled = null;
            if (tokens.size() > 5 && !tokens.get(5).isBlank()) {
                enabled = Boolean.parseBoolean(tokens.get(5).trim());
            }

            items.add(new ImportUserItem(email, firstName, lastName, password, roles, enabled));
        }

        return items;
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        result.add(cur.toString().trim());
        return result;
    }
}
