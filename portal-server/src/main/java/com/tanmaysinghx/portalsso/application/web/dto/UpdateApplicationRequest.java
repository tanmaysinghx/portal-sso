package com.tanmaysinghx.portalsso.application.web.dto;

import com.tanmaysinghx.portalsso.application.entity.ApplicationAccessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpdateApplicationRequest(
        @NotBlank(message = "Application name is required")
        @Size(max = 200, message = "Application name cannot exceed 200 characters")
        String name,

        @Size(max = 1000, message = "Description cannot exceed 1000 characters")
        String description,

        @NotBlank(message = "App URL is required")
        @Size(max = 1000, message = "App URL cannot exceed 1000 characters")
        @Pattern(regexp = "^(https?://).+", message = "App URL must start with http:// or https://")
        String appUrl,

        @Size(max = 1000, message = "Icon URL cannot exceed 1000 characters")
        String iconUrl,

        @Size(max = 100, message = "Category cannot exceed 100 characters")
        String category,

        @Size(max = 100, message = "Client ID cannot exceed 100 characters")
        String clientId,

        ApplicationAccessType accessType,

        List<UUID> roleIds,

        Boolean enabled,

        Integer displayOrder) {}
