package com.tanmaysinghx.portalsso.security.device.entity;

import com.tanmaysinghx.portalsso.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A browser and operating system an account has signed in from before.
 *
 * <p>This exists so a "new device" alert can mean something. It cannot be derived from
 * {@code login_events}: those are purged after 48 hours by the retention job, so any device a user
 * had not touched in two days would look new, and an alert that fires on a familiar laptop every
 * Monday is one nobody reads.
 *
 * <p>{@code user_id} is a plain column rather than a {@code @ManyToOne}. The association is never
 * navigated — the only queries are "is this hash known for this user" and "touch its timestamp" —
 * and keeping it a scalar avoids dragging a lazy {@code User} through the asynchronous
 * notification path that reads it.
 */
@Entity
@Table(
        name = "user_known_devices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_known_devices_user_device",
                columnNames = {"user_id", "device_hash"}))
public class KnownDevice extends BaseEntity {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID userId;

    @Column(name = "device_hash", nullable = false, length = 64)
    private String deviceHash;

    @Column(name = "device_label", length = 255)
    private String deviceLabel;

    @Column(name = "last_ip_address", length = 45)
    private String lastIpAddress;

    @Column(name = "last_country_name", length = 100)
    private String lastCountryName;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected KnownDevice() {
        // JPA
    }

    public KnownDevice(UUID userId, String deviceHash, String deviceLabel, Instant lastSeenAt) {
        this.userId = userId;
        this.deviceHash = deviceHash;
        this.deviceLabel = deviceLabel;
        this.lastSeenAt = lastSeenAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDeviceHash() {
        return deviceHash;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public String getLastIpAddress() {
        return lastIpAddress;
    }

    public void setLastIpAddress(String lastIpAddress) {
        this.lastIpAddress = lastIpAddress;
    }

    public String getLastCountryName() {
        return lastCountryName;
    }

    public void setLastCountryName(String lastCountryName) {
        this.lastCountryName = lastCountryName;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
