package com.tanmaysinghx.portalsso.database.dto;

public record TestConnectionResponse(
        boolean success,
        String message,
        String databaseProduct,
        String databaseVersion,
        String resolvedJdbcUrl) {}
