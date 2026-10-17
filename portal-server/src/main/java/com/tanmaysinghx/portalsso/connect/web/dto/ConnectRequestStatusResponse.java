package com.tanmaysinghx.portalsso.connect.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Lets the app show "waiting for approval" without exposing anything else about the request. */
public record ConnectRequestStatusResponse(UUID requestId, String status, Instant expiresAt) {}
