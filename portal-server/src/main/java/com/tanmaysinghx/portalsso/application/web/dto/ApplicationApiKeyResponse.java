package com.tanmaysinghx.portalsso.application.web.dto;

public record ApplicationApiKeyResponse(
        String apiKey,
        String apiKeyPrefix,
        String headerName,
        String registerEndpoint,
        String loginEndpoint) {}
