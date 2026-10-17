package com.tanmaysinghx.portalsso.connect.web.dto;

import java.time.Instant;
import java.util.UUID;

/** The app sends its user's browser to {@code approvalUrl}; nothing here is secret. */
public record ConnectRequestCreatedResponse(UUID requestId, String approvalUrl, Instant expiresAt) {}
