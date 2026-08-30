package com.tanmaysinghx.portalsso.security.key;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SigningKeyService implements JWKSource<SecurityContext> {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyService.class);

    private final SigningKeyRepository signingKeyRepository;

    public SigningKeyService(SigningKeyRepository signingKeyRepository) {
        this.signingKeyRepository = signingKeyRepository;
    }

    @PostConstruct
    @Transactional
    public void init() {
        if (signingKeyRepository.findByActiveTrueOrderByCreatedAtDesc().isEmpty()) {
            log.info("No active JWT signing key found in database. Generating a new RSA 2048-bit keypair...");
            generateAndSaveNewKey();
        } else {
            log.info("Loaded active JWT signing key(s) from database.");
        }
    }

    @Transactional
    public SigningKey generateAndSaveNewKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

            String keyId = UUID.randomUUID().toString();
            String publicKeyPem = encodePublicKeyToPem(publicKey);
            String privateKeyPem = encodePrivateKeyToPem(privateKey);

            SigningKey signingKey = new SigningKey(keyId, "RS256", publicKeyPem, privateKeyPem, true);
            SigningKey saved = signingKeyRepository.save(signingKey);
            log.info("Saved new JWT signing key with kid='{}'", keyId);
            return saved;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA keypair", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<JWK> get(JWKSelector jwkSelector, SecurityContext context) throws KeySourceException {
        List<SigningKey> keys = signingKeyRepository.findByActiveTrueOrderByCreatedAtDesc();
        if (keys.isEmpty()) {
            // Fallback to all keys if active ones are empty
            keys = signingKeyRepository.findAllByOrderByCreatedAtDesc();
        }

        List<RSAKey> rsaKeys = keys.stream()
                .map(this::toRsaKey)
                .toList();

        JWKSet jwkSet = new JWKSet(rsaKeys.stream().map(k -> (JWK) k).toList());
        List<JWK> selected = jwkSelector.select(jwkSet);

        // If selector matched multiple keys without a specific kid (e.g. NimbusJwtEncoder signing tokens),
        // return only the newest active key so NimbusJwtEncoder does not fail on ambiguous key selection.
        if (selected.size() > 1
                && (jwkSelector.getMatcher().getKeyIDs() == null || jwkSelector.getMatcher().getKeyIDs().isEmpty())
                && jwkSelector.getMatcher().getAlgorithms() != null
                && !jwkSelector.getMatcher().getAlgorithms().isEmpty()) {
            return List.of(selected.get(0));
        }

        return selected;
    }

    public RSAKey toRsaKey(SigningKey entity) {
        try {
            RSAPublicKey publicKey = decodePublicKeyFromPem(entity.getPublicKeyPem());
            RSAPrivateKey privateKey = decodePrivateKeyFromPem(entity.getPrivateKeyPem());

            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(entity.getKeyId())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse RSA key with id=" + entity.getKeyId(), e);
        }
    }

    private static String encodePublicKeyToPem(RSAPublicKey publicKey) {
        String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    private static String encodePrivateKeyToPem(RSAPrivateKey privateKey) {
        String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    private static RSAPublicKey decodePublicKeyFromPem(String pem) throws Exception {
        String cleaned = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    }

    private static RSAPrivateKey decodePrivateKeyFromPem(String pem) throws Exception {
        String cleaned = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }
}
