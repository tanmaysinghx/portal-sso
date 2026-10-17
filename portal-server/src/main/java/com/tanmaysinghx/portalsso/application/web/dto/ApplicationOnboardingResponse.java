package com.tanmaysinghx.portalsso.application.web.dto;

/** The onboarding result: the saved application and its one-time credentials bundle. */
public record ApplicationOnboardingResponse(
        ApplicationResponse application,
        ApplicationCredentialsResponse credentials) {}
