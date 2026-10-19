package com.tanmaysinghx.portalsso.analytics.retention.repository;

import com.tanmaysinghx.portalsso.analytics.retention.entity.AnalyticsRetentionSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsRetentionSettingsRepository extends JpaRepository<AnalyticsRetentionSettings, UUID> {
    Optional<AnalyticsRetentionSettings> findByConfigKey(String configKey);
}
