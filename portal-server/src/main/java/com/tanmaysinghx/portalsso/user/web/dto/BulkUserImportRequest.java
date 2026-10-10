package com.tanmaysinghx.portalsso.user.web.dto;

import java.util.List;
import java.util.Set;

public record BulkUserImportRequest(
        String csvContent,
        List<ImportUserItem> users,
        String defaultPassword,
        Set<String> defaultRoles,
        Boolean defaultEnabled
) {
    public record ImportUserItem(
            String email,
            String firstName,
            String lastName,
            String password,
            Set<String> roles,
            Boolean enabled
    ) {}
}
