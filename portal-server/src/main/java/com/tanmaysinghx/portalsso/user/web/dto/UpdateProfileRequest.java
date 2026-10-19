package com.tanmaysinghx.portalsso.user.web.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 100, message = "First name must be at most 100 characters") String firstName,
        @Size(max = 100, message = "Last name must be at most 100 characters") String lastName) {}
