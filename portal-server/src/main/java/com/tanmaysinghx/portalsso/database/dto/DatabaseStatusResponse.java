package com.tanmaysinghx.portalsso.database.dto;

public record DatabaseStatusResponse(
        String databaseType,
        boolean isEmbedded,
        String jdbcUrl,
        String userName,
        String databaseProductName,
        String databaseProductVersion,
        String driverName,
        String configFilePath) {}
