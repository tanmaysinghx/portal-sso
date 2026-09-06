package com.tanmaysinghx.portalsso.database.dto;

import java.util.Map;

public record MigrateDatabaseResponse(
        boolean success,
        String message,
        int totalRowsMigrated,
        Map<String, Integer> tablesMigrated,
        String targetJdbcUrl,
        String activationInstructions,
        boolean configurationSaved,
        String configFilePath) {}
