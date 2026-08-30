package com.tanmaysinghx.portalsso.user.repository;

import com.tanmaysinghx.portalsso.user.entity.RecoveryCode;
import com.tanmaysinghx.portalsso.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    List<RecoveryCode> findByUserAndUsedAtIsNull(User user);

    List<RecoveryCode> findByUser(User user);

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query("DELETE FROM RecoveryCode r WHERE r.user = :user")
    void deleteByUser(User user);
}
