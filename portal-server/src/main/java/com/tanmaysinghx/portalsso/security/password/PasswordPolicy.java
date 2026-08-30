package com.tanmaysinghx.portalsso.security.password;

import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Enforces {@link PasswordPolicyProperties}. The single place a password is judged. */
@Component
public class PasswordPolicy {

    private final PasswordPolicyProperties properties;

    public PasswordPolicy(PasswordPolicyProperties properties) {
        this.properties = properties;
    }

    /**
     * @throws BusinessRuleViolationException listing <em>every</em> unmet rule, not just the first.
     *     Reporting one at a time turns setting a password into a guessing game where each attempt
     *     reveals one more requirement.
     */
    public void validate(String password) {
        List<String> failures = failures(password);
        if (!failures.isEmpty()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.WEAK_PASSWORD, "Password does not meet policy: " + String.join("; ", failures));
        }
    }

    /** Same rules, without throwing — for callers that report their own way, like the bootstrapper. */
    public List<String> failures(String password) {
        List<String> failures = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            failures.add("must not be empty");
            return failures;
        }
        if (password.length() < properties.minLength()) {
            failures.add("must be at least " + properties.minLength() + " characters");
        }
        if (password.length() > properties.maxLength()) {
            failures.add("must be at most " + properties.maxLength() + " characters");
        }
        if (properties.requireUppercase() && password.chars().noneMatch(Character::isUpperCase)) {
            failures.add("must contain an upper-case letter");
        }
        if (properties.requireLowercase() && password.chars().noneMatch(Character::isLowerCase)) {
            failures.add("must contain a lower-case letter");
        }
        if (properties.requireDigit() && password.chars().noneMatch(Character::isDigit)) {
            failures.add("must contain a digit");
        }
        if (properties.requireSymbol()
                && password.chars().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
            failures.add("must contain a symbol");
        }
        return failures;
    }

    /** The rules in a form the sign-up page can display before the user types anything. */
    public PasswordPolicyDescription describe() {
        return new PasswordPolicyDescription(
                properties.minLength(),
                properties.maxLength(),
                properties.requireUppercase(),
                properties.requireLowercase(),
                properties.requireDigit(),
                properties.requireSymbol());
    }

    public record PasswordPolicyDescription(
            int minLength,
            int maxLength,
            boolean requireUppercase,
            boolean requireLowercase,
            boolean requireDigit,
            boolean requireSymbol) {}
}
