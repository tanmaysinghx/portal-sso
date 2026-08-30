package com.tanmaysinghx.portalsso.security.key;

import com.tanmaysinghx.portalsso.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "signing_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_signing_keys_key_id", columnNames = "key_id"))
public class SigningKey extends BaseEntity {

    @Column(name = "key_id", nullable = false, length = 100)
    private String keyId;

    @Column(name = "algorithm", nullable = false, length = 50)
    private String algorithm;

    @Column(name = "public_key_pem", nullable = false, length = 4000)
    private String publicKeyPem;

    @Column(name = "private_key_pem", nullable = false, length = 4000)
    private String privateKeyPem;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected SigningKey() {
        // JPA
    }

    public SigningKey(String keyId, String algorithm, String publicKeyPem, String privateKeyPem, boolean active) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.publicKeyPem = publicKeyPem;
        this.privateKeyPem = privateKeyPem;
        this.active = active;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
