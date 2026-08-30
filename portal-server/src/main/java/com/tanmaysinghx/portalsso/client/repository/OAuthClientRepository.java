package com.tanmaysinghx.portalsso.client.repository;

import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthClientRepository extends JpaRepository<OAuthClient, UUID> {

    Optional<OAuthClient> findByClientId(String clientId);
}
