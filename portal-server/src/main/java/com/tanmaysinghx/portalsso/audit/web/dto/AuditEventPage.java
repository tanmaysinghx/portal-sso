package com.tanmaysinghx.portalsso.audit.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * A hand-rolled page envelope rather than Spring's {@code Page}, whose JSON shape is unstable across
 * versions and carries internals the console has no use for.
 *
 * <p>The audit log is the first paginated endpoint in the application because it is the first table
 * that only ever grows — every administrative action adds a row and nothing removes one.
 */
public record AuditEventPage(
        List<AuditEventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static AuditEventPage from(Page<?> source, List<AuditEventResponse> content) {
        return new AuditEventPage(
                content, source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
