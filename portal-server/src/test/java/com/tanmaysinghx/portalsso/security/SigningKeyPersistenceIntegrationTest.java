package com.tanmaysinghx.portalsso.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.security.key.SigningKey;
import com.tanmaysinghx.portalsso.security.key.SigningKeyRepository;
import com.tanmaysinghx.portalsso.security.key.SigningKeyService;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SigningKeyPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SigningKeyRepository signingKeyRepository;

    @Autowired
    private SigningKeyService signingKeyService;

    @Autowired
    private JwtDecoder jwtDecoder;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @SuppressWarnings("unchecked")
    void jwksEndpointExposesKeyFromDatabaseWithStableKeyId() throws Exception {
        List<SigningKey> keysInDb = signingKeyRepository.findByActiveTrueOrderByCreatedAtDesc();
        assertThat(keysInDb).isNotEmpty();
        String primaryKid = keysInDb.get(0).getKeyId();

        MvcResult jwksResult = mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> jwksResponse = jsonMapper.readValue(jwksResult.getResponse().getContentAsString(), Map.class);
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwksResponse.get("keys");
        assertThat(keys).isNotEmpty();
        assertThat(keys.stream().anyMatch(k -> primaryKid.equals(k.get("kid")))).isTrue();
    }

    @Test
    void tokenIssuedWithExistingKeyDecodesSuccessfullyAndKeyRotationPublishesAllValidKeys() throws Exception {
        List<SigningKey> initialKeys = signingKeyRepository.findByActiveTrueOrderByCreatedAtDesc();
        assertThat(initialKeys).isNotEmpty();
        SigningKey firstKey = initialKeys.get(0);

        // Encode a token with current signing key
        JwtEncoder encoder = new NimbusJwtEncoder(signingKeyService);
        JwsHeader header = JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                .keyId(firstKey.getKeyId())
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost:8080")
                .subject("testuser@portalsso.local")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Jwt encodedJwt = encoder.encode(JwtEncoderParameters.from(header, claims));

        // Verify it decodes
        Jwt decoded = jwtDecoder.decode(encodedJwt.getTokenValue());
        assertThat(decoded.getSubject()).isEqualTo("testuser@portalsso.local");
        assertThat(decoded.getHeaders().get("kid")).isEqualTo(firstKey.getKeyId());

        // Now simulate key rotation: generate and save a second active key
        SigningKey secondKey = signingKeyService.generateAndSaveNewKey();
        assertThat(secondKey.getKeyId()).isNotEqualTo(firstKey.getKeyId());

        // Both keys should be returned in JWKSet
        var selectedKeys = signingKeyService.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
        assertThat(selectedKeys).hasSizeGreaterThanOrEqualTo(2);
        // The newest key should be first
        assertThat(((RSAKey) selectedKeys.get(0)).getKeyID()).isEqualTo(secondKey.getKeyId());

        // The older token must STILL decode successfully because older valid keys remain in JWKSet
        Jwt decodedOldToken = jwtDecoder.decode(encodedJwt.getTokenValue());
        assertThat(decodedOldToken.getSubject()).isEqualTo("testuser@portalsso.local");
        assertThat(decodedOldToken.getHeaders().get("kid")).isEqualTo(firstKey.getKeyId());
    }
}
