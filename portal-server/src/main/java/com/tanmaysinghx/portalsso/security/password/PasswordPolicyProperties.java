package com.tanmaysinghx.portalsso.security.password;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Password rules, applied everywhere a password is set: admin-created accounts, self-registration,
 * and the startup bootstrap.
 *
 * <p>Length was previously the only check, enforced as a {@code @Size(min = 8)} annotation repeated
 * on each DTO — three places to change and three places to forget. Centralising it means an
 * operator can tighten the rules once, in configuration, and every entry point follows.
 *
 * <p>The defaults are deliberately moderate rather than maximal. Composition rules push people
 * toward predictable substitutions ({@code Password1!}) and NIST 800-63B now advises against
 * mandating them at all in favour of length and breach-list checks. A breach list is the better
 * control and is not implemented here, so these defaults keep a modest composition floor and a
 * length minimum above the old 8.
 */
@ConfigurationProperties(prefix = "app.security.password")
public record PasswordPolicyProperties(
        Integer minLength,
        Integer maxLength,
        Boolean requireUppercase,
        Boolean requireLowercase,
        Boolean requireDigit,
        Boolean requireSymbol) {

    public PasswordPolicyProperties {
        if (minLength == null) minLength = 10;
        // Bounded because the encoder hashes whatever it is given; an unbounded password is a cheap
        // way to make every sign-in attempt expensive.
        if (maxLength == null) maxLength = 100;
        if (requireUppercase == null) requireUppercase = true;
        if (requireLowercase == null) requireLowercase = true;
        if (requireDigit == null) requireDigit = true;
        if (requireSymbol == null) requireSymbol = false;
    }
}
