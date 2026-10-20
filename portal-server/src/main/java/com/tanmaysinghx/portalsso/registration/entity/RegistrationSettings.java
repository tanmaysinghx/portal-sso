package com.tanmaysinghx.portalsso.registration.entity;

import com.tanmaysinghx.portalsso.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Admin-managed public account registration policy. */
@Entity
@Table(name = "registration_settings")
public class RegistrationSettings extends BaseEntity {

    public static final String SINGLETON_KEY = "DEFAULT";

    @Column(name = "config_key", nullable = false, length = 20, unique = true)
    private String configKey = SINGLETON_KEY;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "require_admin_approval", nullable = false)
    private boolean requireAdminApproval;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    protected RegistrationSettings() {}

    public RegistrationSettings(boolean enabled, boolean requireAdminApproval) {
        this.enabled = enabled;
        this.requireAdminApproval = requireAdminApproval;
    }

    public String getConfigKey() { return configKey; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isRequireAdminApproval() { return requireAdminApproval; }
    public void setRequireAdminApproval(boolean requireAdminApproval) { this.requireAdminApproval = requireAdminApproval; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
