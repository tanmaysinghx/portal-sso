package com.tanmaysinghx.portalsso.analytics.retention.entity;

import com.tanmaysinghx.portalsso.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Operator-managed retention windows. A value of zero means keep records indefinitely. */
@Entity
@Table(name = "analytics_retention_settings")
public class AnalyticsRetentionSettings extends BaseEntity {

    public static final String SINGLETON_KEY = "DEFAULT";

    @Column(name = "config_key", nullable = false, length = 20, unique = true)
    private String configKey = SINGLETON_KEY;

    @Column(name = "login_events_hours", nullable = false)
    private int loginEventsHours;

    @Column(name = "audit_events_hours", nullable = false)
    private int auditEventsHours;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    protected AnalyticsRetentionSettings() {}

    public AnalyticsRetentionSettings(int loginEventsHours, int auditEventsHours) {
        this.loginEventsHours = loginEventsHours;
        this.auditEventsHours = auditEventsHours;
    }

    public String getConfigKey() { return configKey; }
    public int getLoginEventsHours() { return loginEventsHours; }
    public void setLoginEventsHours(int loginEventsHours) { this.loginEventsHours = loginEventsHours; }
    public int getAuditEventsHours() { return auditEventsHours; }
    public void setAuditEventsHours(int auditEventsHours) { this.auditEventsHours = auditEventsHours; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
