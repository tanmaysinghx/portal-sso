package com.tanmaysinghx.portalsso.registration.repository;

import com.tanmaysinghx.portalsso.registration.entity.RegistrationSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationSettingsRepository extends JpaRepository<RegistrationSettings, UUID> {
    Optional<RegistrationSettings> findByConfigKey(String configKey);
}
