package com.tanmaysinghx.portalsso.registration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls public self-registration.
 *
 * <p>Defaults to <strong>off</strong>. A Portal SSO account is global rather than scoped to one
 * relying application — whoever registers can sign in to <em>every</em> app behind this server — so
 * leaving the door open by default would be the wrong trade for a self-hosted identity provider.
 * Most deployments are an internal company directory where accounts are provisioned, not
 * self-served.
 *
 * @param enabled whether the public registration endpoint and sign-up page are available at all.
 * @param requireAdminApproval when true, new accounts are created disabled and an administrator
 *     must enable them before the user can sign in. Until email verification exists there is
 *     nothing proving the registrant owns the address they typed, so this is the way to keep a
 *     human in the loop.
 * @param defaultRole the role granted to self-registered users. Never read from the request —
 *     accepting a client-supplied role on an unauthenticated endpoint would be privilege
 *     escalation.
 */
@ConfigurationProperties(prefix = "app.registration")
public record RegistrationProperties(
        boolean enabled, boolean requireAdminApproval, String defaultRole) {

    public RegistrationProperties {
        if (defaultRole == null || defaultRole.isBlank()) {
            defaultRole = "ROLE_USER";
        }
    }
}
