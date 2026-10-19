package com.tanmaysinghx.portalsso.user.web;

import com.tanmaysinghx.portalsso.user.service.AccountService;
import com.tanmaysinghx.portalsso.user.web.dto.AccountResponse;
import com.tanmaysinghx.portalsso.user.web.dto.ChangePasswordRequest;
import com.tanmaysinghx.portalsso.user.web.dto.ChangePasswordResponse;
import com.tanmaysinghx.portalsso.user.web.dto.KnownDeviceResponse;
import com.tanmaysinghx.portalsso.user.web.dto.RecoveryCodesResponse;
import com.tanmaysinghx.portalsso.user.web.dto.RegenerateRecoveryCodesRequest;
import com.tanmaysinghx.portalsso.user.web.dto.UpdateProfileRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service account management for any signed-in user (My Account page).
 *
 * <p>Under {@code /api/user/**} rather than {@code /api/admin/**} because it must work for
 * {@code ROLE_USER}. Two-factor enrolment itself stays at {@code /api/mfa}; this controller covers
 * what surrounds it. Sessions are {@link UserSessionController}.
 */
@RestController
@RequestMapping("/api/user/account")
@PreAuthorize("isAuthenticated()")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public AccountResponse get(Authentication authentication) {
        return accountService.getAccount(authentication.getName());
    }

    @PatchMapping("/profile")
    public AccountResponse updateProfile(
            @Valid @RequestBody UpdateProfileRequest request, Authentication authentication) {
        return accountService.updateProfile(authentication.getName(), request.firstName(), request.lastName());
    }

    @PostMapping("/password")
    public ChangePasswordResponse changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        int signedOut = accountService.changePassword(
                authentication.getName(),
                request.currentPassword(),
                request.newPassword(),
                session == null ? null : session.getId());
        // The credential behind this session just changed; rotate its id like any other
        // privilege-relevant event, so a fixated or leaked id stops working.
        if (session != null) {
            httpRequest.changeSessionId();
        }
        return new ChangePasswordResponse(signedOut);
    }

    @PostMapping("/recovery-codes")
    public RecoveryCodesResponse regenerateRecoveryCodes(
            @Valid @RequestBody RegenerateRecoveryCodesRequest request, Authentication authentication) {
        return new RecoveryCodesResponse(
                accountService.regenerateRecoveryCodes(authentication.getName(), request.code()));
    }

    @GetMapping("/devices")
    public List<KnownDeviceResponse> devices(Authentication authentication, HttpServletRequest request) {
        return accountService.listDevices(authentication.getName(), request.getHeader("User-Agent"));
    }

    @DeleteMapping("/devices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgetDevice(@PathVariable UUID id, Authentication authentication) {
        accountService.forgetDevice(authentication.getName(), id);
    }
}
