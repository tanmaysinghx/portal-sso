package com.tanmaysinghx.portalsso.security.web;

import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.ResourceNotFoundException;
import com.tanmaysinghx.portalsso.security.mfa.MfaService;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service MFA management endpoints.
 *
 * <p>Path choice reasoning: Placed under {@code /api/mfa/**} instead of {@code /api/admin/**}
 * because MFA is a self-service security feature that must be accessible to any authenticated
 * user (including {@code ROLE_USER}), whereas {@code /api/admin/**} is strictly restricted to
 * {@code ROLE_ADMIN}.
 */
@RestController
@RequestMapping("/api/mfa")
@PreAuthorize("isAuthenticated()")
public class MfaController {

    private final MfaService mfaService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public MfaController(
            MfaService mfaService, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.mfaService = mfaService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/status")
    public MfaStatusResponse status(Authentication authentication) {
        User user = getUser(authentication);
        return new MfaStatusResponse(user.isMfaEnabled());
    }

    @PostMapping("/setup")
    public MfaService.MfaSetupResponse setup(Authentication authentication) {
        User user = getUser(authentication);
        return mfaService.initiateSetup(user);
    }

    @PostMapping("/confirm")
    public MfaConfirmResponse confirm(
            @Valid @RequestBody MfaConfirmRequest request, Authentication authentication) {
        User user = getUser(authentication);
        List<String> recoveryCodes = mfaService.confirmSetup(user, request.code());
        return new MfaConfirmResponse(true, recoveryCodes);
    }

    @PostMapping("/disable")
    public MfaStatusResponse disable(
            @Valid @RequestBody MfaDisableRequest request, Authentication authentication) {
        User user = getUser(authentication);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException(
                    ErrorCode.INVALID_CREDENTIALS, "Invalid password confirmation.");
        }
        mfaService.disableMfa(user);
        return new MfaStatusResponse(false);
    }

    private User getUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND, "User not found: " + authentication.getName()));
    }

    public record MfaStatusResponse(boolean mfaEnabled) {}

    public record MfaConfirmRequest(@NotBlank(message = "Verification code is required") String code) {}

    public record MfaConfirmResponse(boolean mfaEnabled, List<String> recoveryCodes) {}

    public record MfaDisableRequest(@NotBlank(message = "Password confirmation is required") String password) {}
}
