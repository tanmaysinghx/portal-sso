package com.tanmaysinghx.portalsso.registration.web.dto;

/**
 * Lets the sign-in page decide whether to offer a "Create an account" link, and lets a relying
 * application decide whether to link users here to sign up.
 *
 * @param enabled whether self-registration is accepted at all.
 * @param requiresApproval whether a new account needs an administrator to enable it, so the UI can
 *     say what will actually happen instead of promising immediate access.
 */
public record RegistrationPolicyResponse(boolean enabled, boolean requiresApproval) {}
