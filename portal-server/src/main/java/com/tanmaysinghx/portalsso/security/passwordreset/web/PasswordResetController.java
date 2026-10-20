package com.tanmaysinghx.portalsso.security.passwordreset.web;

import com.tanmaysinghx.portalsso.common.web.ClientIpResolver;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.security.password.PasswordPolicy.PasswordPolicyDescription;
import com.tanmaysinghx.portalsso.security.passwordreset.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated endpoints behind "Forgot password?". Rate limited per IP in
 * {@code application.yml} and per address in {@link PasswordResetService}.
 *
 * <p>The token travels in request bodies, never a query string, so it does not land in access logs
 * on its way to the server. (The emailed link itself has to carry it in the URL; the reset page
 * strips it from the address bar as soon as it has read it.)
 */
@RestController
@RequestMapping("/api/public/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final PasswordPolicy passwordPolicy;

    public PasswordResetController(PasswordResetService passwordResetService, PasswordPolicy passwordPolicy) {
        this.passwordResetService = passwordResetService;
        this.passwordPolicy = passwordPolicy;
    }

    /** Lets sign-in pages hide "Forgot password?" on a server that cannot send the email. */
    @GetMapping("/policy")
    public PolicyResponse policy() {
        return new PolicyResponse(passwordResetService.isAvailable());
    }

    /** Always the same answer for every address — see {@link PasswordResetService#requestReset}. */
    @PostMapping("/request")
    public MessageResponse request(@Valid @RequestBody ResetRequest body, HttpServletRequest request) {
        passwordResetService.requestReset(
                body.email(), ClientIpResolver.getClientIp(request), request.getHeader("User-Agent"));
        return new MessageResponse(
                "If that address has an account here, a reset link is on its way. It works once and expires soon.");
    }

    @PostMapping("/inspect")
    public InspectResponse inspect(@Valid @RequestBody TokenRequest body) {
        return new InspectResponse(passwordResetService.inspect(body.token()), passwordPolicy.describe());
    }

    @PostMapping("/confirm")
    public MessageResponse confirm(@Valid @RequestBody ConfirmRequest body) {
        passwordResetService.resetPassword(body.token(), body.newPassword());
        return new MessageResponse("Your password has been changed. Sign in with your new password.");
    }

    public record ResetRequest(
            @NotBlank(message = "email is required")
            @Email(message = "email must be a valid email address")
            @Size(max = 320)
            String email) {}

    public record TokenRequest(@NotBlank(message = "token is required") @Size(max = 200) String token) {}

    public record ConfirmRequest(
            @NotBlank(message = "token is required") @Size(max = 200) String token,
            @NotBlank(message = "newPassword is required") String newPassword) {}

    public record PolicyResponse(boolean available) {}

    public record MessageResponse(String message) {}

    public record InspectResponse(String email, PasswordPolicyDescription passwordPolicy) {}
}
