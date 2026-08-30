package com.tanmaysinghx.portalsso.audit.web;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditQueryService;
import com.tanmaysinghx.portalsso.audit.web.dto.AuditEventPage;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only by design. There is no endpoint to edit or delete an entry, and adding one would defeat
 * the point: a trail an administrator can rewrite proves nothing. Retention is therefore an operator
 * concern (a scheduled purge against the database) rather than an application feature — which is
 * also true of {@code login_events}, and still outstanding for both.
 */
@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditQueryService auditQueryService;

    public AdminAuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public AuditEventPage list(
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "targetType", required = false) String targetType,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        return auditQueryService.find(action, actor, targetType, page, size);
    }

    /** Lets the console populate its filter dropdown without hardcoding a copy of the enum. */
    @GetMapping("/actions")
    public List<AuditActionOption> actions() {
        return Arrays.stream(AuditAction.values())
                .map(a -> new AuditActionOption(a.name(), a.getLabel(), a.getTargetType().name()))
                .toList();
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "targetType", required = false) String targetType) {
        String filename = "portal-sso-audit-%s.csv".formatted(LocalDate.now());

        return ResponseEntity.ok()
                // attachment + filename so the browser saves it rather than rendering it inline.
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(auditQueryService.exportCsv(action, actor, targetType));
    }

    public record AuditActionOption(String value, String label, String targetType) {}
}
