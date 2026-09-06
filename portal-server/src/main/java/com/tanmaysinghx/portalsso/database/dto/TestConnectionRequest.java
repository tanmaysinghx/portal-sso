package com.tanmaysinghx.portalsso.database.dto;

import jakarta.validation.constraints.NotBlank;

public record TestConnectionRequest(
        @NotBlank(message = "databaseType is required (POSTGRESQL or MYSQL)")
        String databaseType,
        String host,
        Integer port,
        String databaseName,
        String username,
        String password,
        String customJdbcUrl) {}
