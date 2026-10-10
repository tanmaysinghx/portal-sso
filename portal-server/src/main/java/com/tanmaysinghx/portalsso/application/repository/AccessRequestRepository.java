package com.tanmaysinghx.portalsso.application.repository;

import com.tanmaysinghx.portalsso.application.entity.AccessRequest;
import com.tanmaysinghx.portalsso.application.entity.AccessRequestStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {

    boolean existsByUserIdAndApplicationIdAndStatus(UUID userId, UUID applicationId, AccessRequestStatus status);

    @Query("SELECT ar FROM AccessRequest ar JOIN FETCH ar.user JOIN FETCH ar.application JOIN FETCH ar.reviewer WHERE ar.user.id = :userId ORDER BY ar.createdAt DESC")
    List<AccessRequest> findByUserIdWithDetails(@Param("userId") UUID userId);

    @Query("SELECT ar FROM AccessRequest ar JOIN FETCH ar.user JOIN FETCH ar.application JOIN FETCH ar.reviewer WHERE ar.reviewer.id = :reviewerId AND (:status IS NULL OR ar.status = :status) ORDER BY ar.createdAt DESC")
    List<AccessRequest> findByReviewerIdAndOptionalStatus(@Param("reviewerId") UUID reviewerId, @Param("status") AccessRequestStatus status);

    @Query("SELECT ar FROM AccessRequest ar JOIN FETCH ar.user JOIN FETCH ar.application JOIN FETCH ar.reviewer WHERE (:status IS NULL OR ar.status = :status) ORDER BY ar.createdAt DESC")
    List<AccessRequest> findAllWithDetails(@Param("status") AccessRequestStatus status);

    long countByReviewerIdAndStatus(UUID reviewerId, AccessRequestStatus status);

    @Query("SELECT COUNT(ar) FROM AccessRequest ar WHERE ar.status = :status")
    long countByStatus(@Param("status") AccessRequestStatus status);

    @Query("SELECT ar FROM AccessRequest ar WHERE ar.user.id = :userId AND ar.status = 'PENDING'")
    List<AccessRequest> findPendingByUserId(@Param("userId") UUID userId);
}
