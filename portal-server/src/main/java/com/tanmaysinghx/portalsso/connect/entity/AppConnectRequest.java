package com.tanmaysinghx.portalsso.connect.entity;

import com.tanmaysinghx.portalsso.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An application asking to be connected to this Portal without anyone copying secrets.
 *
 * <p>The app backend submits its manifest and an S256 challenge; a Portal administrator approves
 * it; the app backend then redeems the request with the verifier and receives its credentials.
 * Credentials are generated at redeem time and returned once, so no plaintext secret is ever
 * stored on this row.
 */
@Entity
@Table(name = "app_connect_requests")
public class AppConnectRequest extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppConnectStatus status = AppConnectStatus.PENDING;

    @Column(name = "product", length = 100)
    private String product;

    @Column(name = "app_name", nullable = false, length = 200)
    private String appName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "app_url", nullable = false, length = 1000)
    private String appUrl;

    @Column(name = "icon_url", length = 1000)
    private String iconUrl;

    @Column(name = "callback_uri", nullable = false, length = 1000)
    private String callbackUri;

    @Column(name = "redirect_uris", nullable = false, length = 4000)
    private String redirectUris;

    @Column(name = "scopes", nullable = false, length = 500)
    private String scopes;

    @Column(name = "requested_client_id", nullable = false, length = 100)
    private String requestedClientId;

    @Column(name = "confidential", nullable = false)
    private boolean confidential;

    @Column(name = "external_auth", nullable = false)
    private boolean externalAuth;

    @Column(name = "external_registration", nullable = false)
    private boolean externalRegistration;

    @Column(name = "code_challenge", nullable = false, length = 128)
    private String codeChallenge;

    @Column(name = "delivery_code_hash", length = 64)
    private String deliveryCodeHash;

    @Column(name = "delivery_code_expires_at")
    private Instant deliveryCodeExpiresAt;

    @Column(name = "requester_ip", length = 45)
    private String requesterIp;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "application_id", length = 36, columnDefinition = "VARCHAR(36)")
    private UUID applicationId;

    @Column(name = "reconnect", nullable = false)
    private boolean reconnect;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(name = "failed_redeem_attempts", nullable = false)
    private int failedRedeemAttempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AppConnectRequest() {
        // JPA
    }

    public AppConnectRequest(String appName, String appUrl, String callbackUri, String codeChallenge, Instant expiresAt) {
        this.appName = appName;
        this.appUrl = appUrl;
        this.callbackUri = callbackUri;
        this.codeChallenge = codeChallenge;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public List<String> getRedirectUriList() {
        return split(redirectUris);
    }

    public List<String> getScopeList() {
        return split(scopes);
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.trim().split("\\s+"));
    }

    public AppConnectStatus getStatus() {
        return status;
    }

    public void setStatus(AppConnectStatus status) {
        this.status = status;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getAppName() {
        return appName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAppUrl() {
        return appUrl;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getCallbackUri() {
        return callbackUri;
    }

    public String getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(String redirectUris) {
        this.redirectUris = redirectUris;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getRequestedClientId() {
        return requestedClientId;
    }

    public void setRequestedClientId(String requestedClientId) {
        this.requestedClientId = requestedClientId;
    }

    public boolean isConfidential() {
        return confidential;
    }

    public void setConfidential(boolean confidential) {
        this.confidential = confidential;
    }

    public boolean isExternalAuth() {
        return externalAuth;
    }

    public void setExternalAuth(boolean externalAuth) {
        this.externalAuth = externalAuth;
    }

    public boolean isExternalRegistration() {
        return externalRegistration;
    }

    public void setExternalRegistration(boolean externalRegistration) {
        this.externalRegistration = externalRegistration;
    }

    public String getCodeChallenge() {
        return codeChallenge;
    }

    public String getDeliveryCodeHash() {
        return deliveryCodeHash;
    }

    public void setDeliveryCodeHash(String deliveryCodeHash) {
        this.deliveryCodeHash = deliveryCodeHash;
    }

    public Instant getDeliveryCodeExpiresAt() {
        return deliveryCodeExpiresAt;
    }

    public void setDeliveryCodeExpiresAt(Instant deliveryCodeExpiresAt) {
        this.deliveryCodeExpiresAt = deliveryCodeExpiresAt;
    }

    public String getRequesterIp() {
        return requesterIp;
    }

    public void setRequesterIp(String requesterIp) {
        this.requesterIp = requesterIp;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public boolean isReconnect() {
        return reconnect;
    }

    public void setReconnect(boolean reconnect) {
        this.reconnect = reconnect;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }

    public void setRedeemedAt(Instant redeemedAt) {
        this.redeemedAt = redeemedAt;
    }

    public int getFailedRedeemAttempts() {
        return failedRedeemAttempts;
    }

    public void setFailedRedeemAttempts(int failedRedeemAttempts) {
        this.failedRedeemAttempts = failedRedeemAttempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
