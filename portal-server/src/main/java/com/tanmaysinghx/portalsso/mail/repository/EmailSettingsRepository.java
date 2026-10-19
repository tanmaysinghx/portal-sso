package com.tanmaysinghx.portalsso.mail.repository;

import com.tanmaysinghx.portalsso.mail.entity.EmailSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailSettingsRepository extends JpaRepository<EmailSettings, UUID> {

    Optional<EmailSettings> findByConfigKey(String configKey);
}
