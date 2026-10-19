package com.tanmaysinghx.portalsso.mail.entity;

import com.tanmaysinghx.portalsso.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The server's outbound email configuration — one row, edited by an administrator in the console.
 *
 * <h2>Why this is in the database and not in {@code application.yml}</h2>
 *
 * <p>Everything else about a relay (host, port, credentials) is the kind of setting an operator
 * changes while the server is running, from wherever the console is open. Putting it in
 * {@code spring.mail.*} would mean a restart and filesystem access on the host for every change,
 * and in the single-jar deployment the person administering Portal SSO may have neither.
 *
 * <h2>The password</h2>
 *
 * <p>{@link #passwordEncrypted} holds AES-256-GCM ciphertext, never plaintext, and is never
 * returned by the API — see {@code SmtpSettingsService}. It is nullable because a relay on a
 * private network, or one that authorises by source IP, takes no credentials at all.
 */
@Entity
@Table(name = "smtp_settings")
public class SmtpSettings extends BaseEntity {

    /**
     * The value of {@link #configKey} on the one row that exists. The column is unique, so this is
     * what stops two concurrent first-saves from creating rival configurations.
     */
    public static final String SINGLETON_KEY = "DEFAULT";

    @Column(name = "config_key", nullable = false, length = 20)
    private String configKey = SINGLETON_KEY;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "port")
    private Integer port;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_security", nullable = false, length = 20)
    private SmtpTransportSecurity transportSecurity = SmtpTransportSecurity.STARTTLS;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "password_encrypted", length = 2000)
    private String passwordEncrypted;

    @Column(name = "from_address", length = 320)
    private String fromAddress;

    @Column(name = "from_name", length = 255)
    private String fromName;

    @Column(name = "reply_to_address", length = 320)
    private String replyToAddress;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "last_test_successful")
    private Boolean lastTestSuccessful;

    @Column(name = "last_test_error", length = 1000)
    private String lastTestError;

    public String getConfigKey() {
        return configKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public SmtpTransportSecurity getTransportSecurity() {
        return transportSecurity;
    }

    public void setTransportSecurity(SmtpTransportSecurity transportSecurity) {
        this.transportSecurity = transportSecurity;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordEncrypted() {
        return passwordEncrypted;
    }

    public void setPasswordEncrypted(String passwordEncrypted) {
        this.passwordEncrypted = passwordEncrypted;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getReplyToAddress() {
        return replyToAddress;
    }

    public void setReplyToAddress(String replyToAddress) {
        this.replyToAddress = replyToAddress;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getLastTestAt() {
        return lastTestAt;
    }

    public void setLastTestAt(Instant lastTestAt) {
        this.lastTestAt = lastTestAt;
    }

    public Boolean getLastTestSuccessful() {
        return lastTestSuccessful;
    }

    public void setLastTestSuccessful(Boolean lastTestSuccessful) {
        this.lastTestSuccessful = lastTestSuccessful;
    }

    public String getLastTestError() {
        return lastTestError;
    }

    public void setLastTestError(String lastTestError) {
        this.lastTestError = lastTestError;
    }

    /** True when a password is stored. Used to tell the console a secret exists without sending it. */
    public boolean hasPassword() {
        return passwordEncrypted != null && !passwordEncrypted.isBlank();
    }

    /**
     * Whether this configuration is complete enough to attempt a send. {@link #enabled} alone is
     * not sufficient: a half-filled row that was saved while disabled must not start being used the
     * moment someone flicks the switch.
     */
    public boolean isUsable() {
        return enabled
                && host != null && !host.isBlank()
                && port != null
                && fromAddress != null && !fromAddress.isBlank();
    }
}
