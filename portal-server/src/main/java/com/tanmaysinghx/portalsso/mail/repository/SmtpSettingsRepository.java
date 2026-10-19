package com.tanmaysinghx.portalsso.mail.repository;

import com.tanmaysinghx.portalsso.mail.entity.SmtpSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmtpSettingsRepository extends JpaRepository<SmtpSettings, UUID> {

    /**
     * Reads the single configuration row. Keyed on the fixed {@code config_key} rather than "first
     * row by id" so the query states the single-row invariant the unique constraint enforces.
     */
    Optional<SmtpSettings> findByConfigKey(String configKey);
}
