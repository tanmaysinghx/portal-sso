package com.tanmaysinghx.portalsso.application.repository;

import com.tanmaysinghx.portalsso.application.entity.Application;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    @Query("SELECT DISTINCT a FROM Application a LEFT JOIN FETCH a.roles ORDER BY a.displayOrder ASC, a.name ASC")
    List<Application> findAllWithRoles();

    @Query("SELECT a FROM Application a LEFT JOIN FETCH a.roles WHERE a.id = :id")
    Optional<Application> findByIdWithRoles(@Param("id") UUID id);

    @Query("SELECT DISTINCT a FROM Application a WHERE a.enabled = true ORDER BY a.displayOrder ASC, a.name ASC")
    List<Application> findAllEnabled();

    @Query("SELECT DISTINCT a FROM Application a WHERE a.enabled = true AND a.accessType = com.tanmaysinghx.portalsso.application.entity.ApplicationAccessType.ALL_USERS ORDER BY a.displayOrder ASC, a.name ASC")
    List<Application> findAllUsersEnabled();

    @Query("SELECT DISTINCT a FROM Application a LEFT JOIN a.roles r WHERE a.enabled = true AND (a.accessType = com.tanmaysinghx.portalsso.application.entity.ApplicationAccessType.ALL_USERS OR r.name IN :roleNames) ORDER BY a.displayOrder ASC, a.name ASC")
    List<Application> findAccessibleApplications(@Param("roleNames") Collection<String> roleNames);
}
