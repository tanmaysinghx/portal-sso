package com.tanmaysinghx.portalsso.connect.web.dto;

import com.tanmaysinghx.portalsso.application.web.dto.ApplicationCredentialsResponse;
import java.util.UUID;

/**
 * Returned exactly once, to the backend that proved both the delivery code and the verifier.
 * {@code credentials} carries the client secret and API key in plaintext; Portal keeps only hashes.
 */
public record ConnectRedeemResponse(
        UUID applicationId,
        String applicationName,
        ApplicationCredentialsResponse credentials) {}
