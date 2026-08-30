package com.tanmaysinghx.portalsso.audit.service;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.entity.AuditEvent;
import com.tanmaysinghx.portalsso.audit.repository.AuditEventRepository;
import com.tanmaysinghx.portalsso.audit.web.dto.AuditEventPage;
import com.tanmaysinghx.portalsso.audit.web.dto.AuditEventResponse;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the audit trail: the admin console's log screen and its CSV export. */
@Service
public class AuditQueryService {

    /** Large enough to be useful, small enough that one page is never a de-facto full scan. */
    static final int MAX_PAGE_SIZE = 200;
    static final int DEFAULT_PAGE_SIZE = 25;
    /** Ceiling on a single export, so "download everything" cannot exhaust heap on a large table. */
    static final int MAX_EXPORT_ROWS = 10_000;

    private final AuditEventRepository repository;

    public AuditQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AuditEventPage find(String action, String actor, String targetType, int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                // Ties on occurred_at are broken by id so paging is stable: without a total order,
                // rows written in the same millisecond can appear on two pages or on neither.
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<AuditEvent> result = repository.findAll(filter(action, actor, targetType), pageRequest);
        return AuditEventPage.from(result, result.getContent().stream().map(AuditEventResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public String exportCsv(String action, String actor, String targetType) {
        PageRequest pageRequest = PageRequest.of(
                0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")));

        StringBuilder csv = new StringBuilder(
                "occurred_at,actor_email,action,target_type,target_id,target_label,details,ip_address,user_agent\n");
        for (AuditEvent e : repository.findAll(filter(action, actor, targetType), pageRequest)) {
            csv.append(csvCell(e.getOccurredAt() == null ? "" : e.getOccurredAt().toString())).append(',')
                    .append(csvCell(e.getActorEmail())).append(',')
                    .append(csvCell(e.getAction().name())).append(',')
                    .append(csvCell(e.getTargetType().name())).append(',')
                    .append(csvCell(e.getTargetId())).append(',')
                    .append(csvCell(e.getTargetLabel())).append(',')
                    .append(csvCell(e.getDetails())).append(',')
                    .append(csvCell(e.getIpAddress())).append(',')
                    .append(csvCell(e.getUserAgent())).append('\n');
        }
        return csv.toString();
    }

    /**
     * Each filter contributes a predicate only when supplied, so an absent filter is simply not part
     * of the query. The alternative — a JPQL {@code (:param is null or e.action = :param)} guard —
     * needs an explicit type hint for a null enum parameter and reads worse for it.
     */
    private static Specification<AuditEvent> filter(String action, String actor, String targetType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(action)) {
                predicates.add(cb.equal(root.get("action"), parseAction(action)));
            }
            if (hasText(targetType)) {
                predicates.add(cb.equal(root.get("targetType"), parseTargetType(targetType)));
            }
            if (hasText(actor)) {
                // Substring match so a partial address works. escape() on the input keeps a stray
                // % or _ in an email from turning into a wildcard.
                predicates.add(cb.like(
                        cb.lower(root.get("actorEmail")), "%" + escapeLike(actor.toLowerCase(Locale.ROOT)) + "%", '\\'));
            }

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static AuditAction parseAction(String value) {
        try {
            return AuditAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // A 400 naming the valid values, rather than a 500 from the enum, or — worse — silently
            // ignoring the filter and returning rows the caller did not ask for.
            throw new BusinessRuleViolationException(
                    ErrorCode.VALIDATION_FAILED, "Unknown audit action '" + value + "'.");
        }
    }

    private static AuditAction.TargetType parseTargetType(String value) {
        try {
            return AuditAction.TargetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException(
                    ErrorCode.VALIDATION_FAILED, "Unknown audit target type '" + value + "'.");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Quotes every cell and doubles embedded quotes; a user agent contains commas as a matter of
     * course, so unquoted output would silently shift columns.
     *
     * <p>A leading {@code = + - @} is also prefixed with a quote. Cells here carry operator-supplied
     * text — client names, emails — and a spreadsheet treats a leading {@code =} as a formula to
     * execute when the exported file is opened.
     */
    private static String csvCell(String value) {
        if (value == null) {
            return "\"\"";
        }
        String cell = value;
        if (!cell.isEmpty() && "=+-@".indexOf(cell.charAt(0)) >= 0) {
            cell = "'" + cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }
}
