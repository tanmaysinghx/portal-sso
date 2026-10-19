package com.tanmaysinghx.portalsso.security.magiclink.web;

import com.tanmaysinghx.portalsso.common.web.ClientIpResolver;
import com.tanmaysinghx.portalsso.security.magiclink.service.MagicLinkService;
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
 * Unauthenticated endpoints behind the "email me a link" button.
 *
 * <p>Rate limited per IP in {@code application.yml} and per address in {@link MagicLinkService}.
 * Both are needed and neither substitutes for the other: the per-IP limit stops one source
 * generating volume, and the per-address limit stops many sources flooding one mailbox.
 */
@RestController
@RequestMapping("/api/public/magic-link")
public class MagicLinkController {

    private final MagicLinkService magicLinkService;

    public MagicLinkController(MagicLinkService magicLinkService) {
        this.magicLinkService = magicLinkService;
    }

    /** Lets the sign-in page decide whether to offer passwordless sign-in at all. */
    @GetMapping("/policy")
    public MagicLinkPolicyResponse policy() {
        return new MagicLinkPolicyResponse(
                magicLinkService.isEnabled(), magicLinkService.isRegistrationEnabled());
    }

    /**
     * Requests a link.
     *
     * <p>Always answers the same way when the feature is on — see {@link MagicLinkService} on why
     * this endpoint does not reveal whether an account exists. The response text is deliberately
     * written to be true in every case, including the ones where nothing was sent.
     */
    @PostMapping("/request")
    public MagicLinkRequestResponse request(
            @Valid @RequestBody MagicLinkRequest body, HttpServletRequest request) {
        magicLinkService.requestLink(
                body.email(), ClientIpResolver.getClientIp(request), request.getHeader("User-Agent"));

        return new MagicLinkRequestResponse(
                "If that address can sign in here, a link is on its way. It expires shortly and works once.");
    }

    public record MagicLinkRequest(
            @NotBlank(message = "email is required")
            @Email(message = "email must be a valid email address")
            @Size(max = 320)
            String email) {}

    public record MagicLinkRequestResponse(String message) {}

    public record MagicLinkPolicyResponse(boolean enabled, boolean registrationEnabled) {}
}
