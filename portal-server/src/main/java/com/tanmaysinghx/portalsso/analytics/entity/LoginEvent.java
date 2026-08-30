package com.tanmaysinghx.portalsso.analytics.entity;

import com.tanmaysinghx.portalsso.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One recorded sign-in attempt, successful or not. Append-only: nothing updates a row after it is
 * written, so this doubles as the audit trail for authentication.
 */
@Entity
@Table(name = "login_events")
public class LoginEvent extends BaseEntity {

    /** Null when the attempt was for an address that has no account. */
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "successful", nullable = false)
    private boolean successful;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "country_name", length = 100)
    private String countryName;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected LoginEvent() {
        // JPA
    }

    public LoginEvent(String email, boolean successful, Instant occurredAt) {
        this.email = email;
        this.successful = successful;
        this.occurredAt = occurredAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
