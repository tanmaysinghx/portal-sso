package com.tanmaysinghx.portalsso.security.key;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SigningKeyRepository extends JpaRepository<SigningKey, UUID> {

    List<SigningKey> findByActiveTrueOrderByCreatedAtDesc();

    List<SigningKey> findAllByOrderByCreatedAtDesc();

    Optional<SigningKey> findByKeyId(String keyId);

    boolean existsByActiveTrue();
}
