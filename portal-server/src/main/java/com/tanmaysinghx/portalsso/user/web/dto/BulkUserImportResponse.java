package com.tanmaysinghx.portalsso.user.web.dto;

import java.util.List;

public record BulkUserImportResponse(
        int totalProcessed,
        int importedCount,
        int skippedCount,
        List<String> errors
) {}
