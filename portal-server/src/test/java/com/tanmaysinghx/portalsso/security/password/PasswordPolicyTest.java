package com.tanmaysinghx.portalsso.security.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private static PasswordPolicy policy(PasswordPolicyProperties props) {
        return new PasswordPolicy(props);
    }

    private static final PasswordPolicyProperties DEFAULTS =
            new PasswordPolicyProperties(null, null, null, null, null, null);

    @Test
    void theDefaultsAcceptAReasonablePassword() {
        assertThat(policy(DEFAULTS).failures("SecurePassword123!")).isEmpty();
    }

    @Test
    void aShortPasswordIsRejected() {
        assertThat(policy(DEFAULTS).failures("Ab1cdef")).anyMatch(f -> f.contains("at least 10"));
    }

    /**
     * Every unmet rule is reported at once. One at a time turns setting a password into a guessing
     * game where each rejection reveals a single further requirement.
     */
    @Test
    void allUnmetRulesAreReportedTogether() {
        assertThat(policy(DEFAULTS).failures("short")).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void anOverlongPasswordIsRejected() {
        // Unbounded input would let anyone make every sign-in attempt expensive to hash.
        assertThat(policy(DEFAULTS).failures("Aa1" + "x".repeat(200)))
                .anyMatch(f -> f.contains("at most"));
    }

    @Test
    void requirementsCanBeTurnedOff() {
        PasswordPolicyProperties relaxed =
                new PasswordPolicyProperties(8, 100, false, false, false, false);
        assertThat(policy(relaxed).failures("allsmall")).isEmpty();
    }

    @Test
    void requiringASymbolIsOffByDefaultAndCanBeTurnedOn() {
        assertThat(policy(DEFAULTS).failures("NoSymbolHere1")).isEmpty();

        PasswordPolicyProperties strict =
                new PasswordPolicyProperties(10, 100, true, true, true, true);
        assertThat(policy(strict).failures("NoSymbolHere1")).anyMatch(f -> f.contains("symbol"));
        assertThat(policy(strict).failures("HasSymbolHere1!")).isEmpty();
    }

    @Test
    void validateThrowsWithEveryReasonInTheMessage() {
        assertThatThrownBy(() -> policy(DEFAULTS).validate("short"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("at least 10")
                .hasMessageContaining("upper-case");
    }

    @Test
    void aNullOrEmptyPasswordIsRejectedRatherThanCrashing() {
        assertThat(policy(DEFAULTS).failures(null)).isNotEmpty();
        assertThat(policy(DEFAULTS).failures("")).isNotEmpty();
    }

    @Test
    void theDescriptionMirrorsTheConfiguredRules() {
        var described = policy(new PasswordPolicyProperties(14, 64, true, true, false, true)).describe();
        assertThat(described.minLength()).isEqualTo(14);
        assertThat(described.maxLength()).isEqualTo(64);
        assertThat(described.requireDigit()).isFalse();
        assertThat(described.requireSymbol()).isTrue();
    }
}
