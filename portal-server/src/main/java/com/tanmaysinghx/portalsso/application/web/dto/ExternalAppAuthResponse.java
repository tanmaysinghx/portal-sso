package com.tanmaysinghx.portalsso.application.web.dto;

import java.util.List;
import java.util.UUID;

public record ExternalAppAuthResponse(
        boolean authenticated,
        boolean mfaRequired,
        UserSummary user) {

    public record UserSummary(
            UUID id,
            String email,
            String firstName,
            String lastName,
            List<String> roles) {}
}
