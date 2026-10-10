package com.tanmaysinghx.portalsso.application.web.dto;

import java.util.UUID;

public record AdminSummaryResponse(UUID id, String email, String firstName, String lastName) {}
