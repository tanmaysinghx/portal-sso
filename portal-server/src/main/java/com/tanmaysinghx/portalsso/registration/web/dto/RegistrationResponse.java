package com.tanmaysinghx.portalsso.registration.web.dto;

/**
 * Deliberately thin: an unauthenticated caller gets back only what it needs to render a
 * confirmation. No user id, no roles, no timestamps.
 *
 * @param pendingApproval true when the account was created disabled and cannot sign in yet.
 */
public record RegistrationResponse(String email, boolean pendingApproval) {}
