package com.tanmaysinghx.portalsso.security.device.repository;

import com.tanmaysinghx.portalsso.security.device.entity.KnownDevice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnownDeviceRepository extends JpaRepository<KnownDevice, UUID> {

    Optional<KnownDevice> findByUserIdAndDeviceHash(UUID userId, String deviceHash);

    List<KnownDevice> findByUserIdOrderByLastSeenAtDesc(UUID userId);

    /** Used when an administrator resets an account, so its devices stop counting as recognised. */
    void deleteByUserId(UUID userId);
}
