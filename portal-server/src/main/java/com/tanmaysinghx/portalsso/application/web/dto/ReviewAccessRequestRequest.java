package com.tanmaysinghx.portalsso.application.web.dto;

import jakarta.validation.constraints.Size;

public record ReviewAccessRequestRequest(
        @Size(max = 500, message = "Comment cannot exceed 500 characters") String comment) {}
