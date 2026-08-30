package com.tanmaysinghx.portalsso.audit.repository;

import com.tanmaysinghx.portalsso.audit.entity.AuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Note the absence of any delete method. The table is append-only by design, and Spring Data would
 * otherwise hand every caller {@code deleteAll()} for free.
 *
 * <p>Filtering goes through {@link JpaSpecificationExecutor} rather than a JPQL query with
 * {@code (:param is null or ...)} guards: Hibernate needs an explicit type for a null enum
 * parameter, and the criteria API sidesteps that by simply omitting the predicate.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {
}
