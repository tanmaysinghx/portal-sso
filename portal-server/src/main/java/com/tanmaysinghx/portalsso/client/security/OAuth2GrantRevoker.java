package com.tanmaysinghx.portalsso.client.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes every stored authorization and consent belonging to a registered client.
 *
 * <p>Spring Authorization Server's reference schema links {@code oauth2_authorization} and
 * {@code oauth2_authorization_consent} to a client by a plain {@code registered_client_id} column
 * with no foreign key, so nothing cascades. Deleting a client without this would leave its access
 * tokens, refresh tokens and consent records behind forever — a slow storage leak, and a
 * surprising one, since an operator who deletes a client reasonably expects its grants to be gone.
 *
 * <p>Uses {@link JdbcTemplate} rather than a JPA repository because those two tables are owned by
 * {@code JdbcOAuth2AuthorizationService}, not mapped as entities in this codebase.
 */
@Component
public class OAuth2GrantRevoker {

    private static final Logger log = LoggerFactory.getLogger(OAuth2GrantRevoker.class);

    private final JdbcTemplate jdbcTemplate;

    public OAuth2GrantRevoker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param registeredClientId the client's primary key (the {@code id} column), which is what
     *     these tables store — not the human-facing {@code client_id}.
     */
    @Transactional
    public void revokeAllFor(String registeredClientId) {
        int authorizations =
                jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", registeredClientId);
        int consents = jdbcTemplate.update(
                "DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", registeredClientId);

        if (authorizations > 0 || consents > 0) {
            log.info(
                    "Revoked grants for client {}: {} authorization(s), {} consent(s)",
                    registeredClientId,
                    authorizations,
                    consents);
        }
    }
}
