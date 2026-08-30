package com.tanmaysinghx.portalsso.registration.web;

import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.ResourceConflictException;
import com.tanmaysinghx.portalsso.registration.config.RegistrationProperties;
import com.tanmaysinghx.portalsso.registration.web.dto.RegisterRequest;
import com.tanmaysinghx.portalsso.registration.web.dto.RegistrationPolicyResponse;
import com.tanmaysinghx.portalsso.registration.web.dto.RegistrationResponse;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public self-registration, so users can create their own account instead of waiting for an
 * administrator.
 *
 * <p>This is the only unauthenticated write endpoint in the application, so the constraints are
 * deliberate:
 *
 * <ul>
 *   <li><strong>Off by default.</strong> When {@code app.registration.enabled} is false the
 *       endpoint refuses everything, so an operator has to opt in.
 *   <li><strong>The server picks the role and the enabled flag</strong>, never the request — see
 *       {@link RegisterRequest}.
 *   <li><strong>Relying applications link here; they do not create users.</strong> A Portal SSO
 *       account grants access to every application behind this server, so no single application
 *       gets to decide who exists. Apps read {@code GET /api/public/registration-policy} to decide
 *       whether to show a "Sign up" link that points at this server's own page.
 * </ul>
 *
 * <p>Known gap: there is no rate limiting yet, so a public deployment can be spammed with
 * registrations, and no email verification, so an address is unproven until that lands. Turning on
 * {@code require-admin-approval} keeps a human in the loop in the meantime.
 */
@RestController
@RequestMapping("/api/public")
public class PublicRegistrationController {

    private static final Logger log = LoggerFactory.getLogger(PublicRegistrationController.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationProperties properties;

    public PublicRegistrationController(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            RegistrationProperties properties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /** Readable whether or not registration is on — that answer is the whole point of it. */
    @GetMapping("/registration-policy")
    public RegistrationPolicyResponse policy() {
        return new RegistrationPolicyResponse(properties.enabled(), properties.requireAdminApproval());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RegistrationResponse register(@Valid @RequestBody RegisterRequest request) {
        if (!properties.enabled()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.REGISTRATION_DISABLED, "Self-registration is not enabled on this server.");
        }

        String email = request.email().trim().toLowerCase();

        // This does tell an anonymous caller which addresses are registered. The alternative —
        // pretending to succeed — leaves a real user unable to explain why they can never sign in,
        // so the usability cost outweighs the enumeration risk here. Rate limiting is the control
        // that actually blunts enumeration, and it is still outstanding.
        if (userRepository.existsByEmail(email)) {
            throw new ResourceConflictException(
                    ErrorCode.USER_ALREADY_EXISTS, "An account with that email already exists.");
        }

        Role role = roleRepository
                .findByName(properties.defaultRole())
                .orElseGet(() -> roleRepository.save(new Role(properties.defaultRole(), "Self-registered user")));

        User user = new User(email, passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(!properties.requireAdminApproval());
        user.addRole(role);

        userRepository.save(user);
        log.info("Self-registered account '{}' (pendingApproval={})", email, properties.requireAdminApproval());

        return new RegistrationResponse(email, properties.requireAdminApproval());
    }
}
