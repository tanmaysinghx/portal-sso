package com.tanmaysinghx.portalsso.connect.service;

import com.tanmaysinghx.portalsso.application.entity.Application;
import com.tanmaysinghx.portalsso.application.entity.ApplicationAccessType;
import com.tanmaysinghx.portalsso.application.repository.ApplicationRepository;
import com.tanmaysinghx.portalsso.application.service.ApplicationOnboardingService;
import com.tanmaysinghx.portalsso.application.web.dto.ApplicationCredentialsResponse;
import com.tanmaysinghx.portalsso.application.web.dto.ApplicationOnboardingResponse;
import com.tanmaysinghx.portalsso.application.web.dto.CreateApplicationRequest;
import com.tanmaysinghx.portalsso.application.web.dto.OnboardApplicationRequest;
import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.service.AuditService;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.PortalException;
import com.tanmaysinghx.portalsso.common.error.ResourceNotFoundException;
import com.tanmaysinghx.portalsso.connect.KnownProducts;
import com.tanmaysinghx.portalsso.connect.config.AppConnectProperties;
import com.tanmaysinghx.portalsso.connect.entity.AppConnectRequest;
import com.tanmaysinghx.portalsso.connect.entity.AppConnectStatus;
import com.tanmaysinghx.portalsso.connect.repository.AppConnectRequestRepository;
import com.tanmaysinghx.portalsso.connect.web.dto.ConnectRedeemResponse;
import com.tanmaysinghx.portalsso.connect.web.dto.ConnectRequestCreatedResponse;
import com.tanmaysinghx.portalsso.connect.web.dto.ConnectRequestStatusResponse;
import com.tanmaysinghx.portalsso.connect.web.dto.ConnectRequestSubmission;
import com.tanmaysinghx.portalsso.connect.web.dto.ConnectRequestSummary;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * "Connect with Portal SSO": an app registers itself and receives its credentials without a person
 * ever copying a secret.
 *
 * <ol>
 *   <li>The app <em>backend</em> submits its manifest, a callback URI and an S256 challenge.
 *   <li>A Portal administrator reviews the request at {@code /connect/{id}} and approves or denies.
 *       Approval creates the application and OAuth client through the normal onboarding path.
 *   <li>Portal redirects the browser to the app's callback with a one-time delivery code.
 *   <li>The app backend redeems the code together with the verifier and receives freshly
 *       generated credentials, exactly once.
 * </ol>
 *
 * <p>Two proofs are needed to redeem, and they defend against different attackers. The delivery
 * code is only ever sent to the callback URI, which must share the app's origin, so it proves the
 * redeemer really serves the address the administrator approved. The verifier proves the redeemer
 * is the backend that submitted the request, so a code leaked from a URL or log is useless alone.
 * Without the code, anyone could submit a request claiming another app's address, wait for an
 * administrator to approve it, and collect that app's rotated secrets.
 */
@Service
public class AppConnectService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final List<String> DEFAULT_SCOPES = List.of("openid", "profile", "email");
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private final AppConnectRequestRepository repository;
    private final ApplicationOnboardingService onboardingService;
    private final ApplicationRepository applicationRepository;
    private final OAuthClientRepository oAuthClientRepository;
    private final AuditService auditService;
    private final AppConnectProperties properties;
    private final String issuerUrl;
    private Clock clock = Clock.systemUTC();

    public AppConnectService(
            AppConnectRequestRepository repository,
            ApplicationOnboardingService onboardingService,
            ApplicationRepository applicationRepository,
            OAuthClientRepository oAuthClientRepository,
            AuditService auditService,
            AppConnectProperties properties,
            @Value("${spring.security.oauth2.authorizationserver.issuer:http://localhost:8080}") String issuerUrl) {
        this.repository = repository;
        this.onboardingService = onboardingService;
        this.applicationRepository = applicationRepository;
        this.oAuthClientRepository = oAuthClientRepository;
        this.auditService = auditService;
        this.properties = properties;
        this.issuerUrl = issuerUrl.endsWith("/") ? issuerUrl.substring(0, issuerUrl.length() - 1) : issuerUrl;
    }

    /** Tests move time forward without sleeping. */
    @Autowired(required = false)
    void setClock(Clock clock) {
        this.clock = clock;
    }

    // ------------------------------------------------------------------ submit (app backend)

    @Transactional
    public ConnectRequestCreatedResponse submit(ConnectRequestSubmission submission, String requesterIp) {
        requireEnabled();
        Instant now = clock.instant();
        if (repository.countByStatusAndExpiresAtAfter(AppConnectStatus.PENDING, now) >= properties.maxPending()) {
            throw new PortalException(ErrorCode.CONNECT_TOO_MANY_PENDING);
        }

        ConnectRequestSubmission.AppManifest manifest = submission.manifest();
        URI appUrl = parseAppUri(manifest.appUrl(), "manifest.appUrl");
        String origin = origin(appUrl);
        String callbackUri = requireSameOrigin(submission.callbackUri(), origin, "callbackUri");

        Set<String> redirectUris = new LinkedHashSet<>();
        if (manifest.redirectUris() != null) {
            for (String uri : manifest.redirectUris()) {
                if (uri != null && !uri.isBlank()) {
                    redirectUris.add(requireSameOrigin(uri, origin, "manifest.redirectUris"));
                }
            }
        }
        if (redirectUris.isEmpty()) {
            redirectUris.add(appUrl.toString());
        }

        Set<String> scopes = new LinkedHashSet<>();
        scopes.add("openid");
        scopes.addAll(manifest.scopes() == null || manifest.scopes().isEmpty() ? DEFAULT_SCOPES : manifest.scopes());

        String iconUrl = blankToNull(manifest.iconUrl());
        if (iconUrl != null) {
            // Rendered on the approval page; an off-origin image would let anyone track who reviews.
            iconUrl = requireSameOrigin(iconUrl, origin, "manifest.iconUrl");
        }

        String product = blankToNull(manifest.product());
        String clientId = firstNonBlank(manifest.clientId(), product, ApplicationOnboardingService.toClientId(manifest.name()));
        if (clientId == null) {
            throw invalid("Could not derive a client_id; set manifest.clientId.");
        }

        AppConnectRequest request = new AppConnectRequest(
                manifest.name().trim(), appUrl.toString(), callbackUri, submission.codeChallenge(),
                now.plus(properties.pendingTtl()));
        request.setProduct(product);
        request.setDescription(blankToNull(manifest.description()));
        request.setIconUrl(iconUrl);
        request.setRedirectUris(String.join(" ", redirectUris));
        request.setScopes(String.join(" ", scopes));
        request.setRequestedClientId(clientId);
        request.setConfidential(manifest.confidential() == null || manifest.confidential());
        request.setExternalAuth(Boolean.TRUE.equals(manifest.externalAuth()));
        request.setExternalRegistration(Boolean.TRUE.equals(manifest.externalAuth()) && Boolean.TRUE.equals(manifest.externalRegistration()));
        request.setRequesterIp(requesterIp);
        AppConnectRequest saved = repository.save(request);

        return new ConnectRequestCreatedResponse(saved.getId(), issuerUrl + "/connect/" + saved.getId(), saved.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public ConnectRequestStatusResponse status(UUID id) {
        AppConnectRequest request = find(id);
        return new ConnectRequestStatusResponse(id, statusLabel(request), request.getExpiresAt());
    }

    // ------------------------------------------------------------------ review (administrator)

    /** What the approval page shows. Computed fresh on every view, never trusted from the form. */
    public record Review(
            AppConnectRequest request,
            KnownProducts.Product knownProduct,
            String origin,
            boolean insecureTransport,
            String status,
            boolean reviewable,
            boolean resumable,
            String reconnectApplicationName,
            String clientIdToCreate) {}

    @Transactional(readOnly = true)
    public Review review(UUID id) {
        AppConnectRequest request = find(id);
        Instant now = clock.instant();
        String origin = origin(URI.create(request.getAppUrl()));
        Optional<Application> reconnectTarget = reconnectTarget(request);
        String status = statusLabel(request);
        return new Review(
                request,
                KnownProducts.find(request.getProduct()).orElse(null),
                origin,
                origin.startsWith("http://"),
                status,
                request.getStatus() == AppConnectStatus.PENDING && !request.isExpired(now),
                request.getStatus() == AppConnectStatus.APPROVED && !request.isExpired(now),
                reconnectTarget.map(Application::getName).orElse(null),
                reconnectTarget.isPresent() ? null : availableClientId(request.getRequestedClientId()));
    }

    @Transactional(readOnly = true)
    public List<ConnectRequestSummary> listPending() {
        return repository.findByStatusAndExpiresAtAfterOrderByCreatedAtDesc(AppConnectStatus.PENDING, clock.instant())
                .stream().map(ConnectRequestSummary::from).toList();
    }

    /**
     * Approves the request and returns the callback URL (with a fresh delivery code) to send the
     * browser to. The administrator may withhold the external login/register API even when the
     * app asked for it: that API verifies user passwords, so it is the most sensitive grant here.
     */
    @Transactional
    public String approve(UUID id, boolean allowExternalAuth, boolean allowExternalRegistration, String reviewer) {
        AppConnectRequest request = requireReviewable(id);
        Optional<Application> reconnectTarget = reconnectTarget(request);

        UUID applicationId;
        if (reconnectTarget.isPresent()) {
            // Same origin and client as an app already here: a reinstall. Nothing is created and its
            // settings are kept; its secrets are rotated when the app redeems.
            applicationId = reconnectTarget.get().getId();
            request.setReconnect(true);
        } else {
            boolean externalAuth = request.isExternalAuth() && allowExternalAuth;
            boolean externalRegistration = externalAuth && request.isExternalRegistration() && allowExternalRegistration;
            String category = KnownProducts.find(request.getProduct()).map(KnownProducts.Product::category).orElse(null);
            // Onboarding generates a secret and key here too. They are discarded unseen and replaced at
            // redeem time, so the only copy of a working credential is the one the app receives.
            ApplicationOnboardingResponse created = onboardingService.onboard(new OnboardApplicationRequest(
                    new CreateApplicationRequest(
                            request.getAppName(), request.getDescription(), request.getAppUrl(), request.getIconUrl(),
                            category, null, ApplicationAccessType.ALL_USERS, null, true, null,
                            externalAuth, externalRegistration),
                    new OnboardApplicationRequest.NewOAuthClient(
                            availableClientId(request.getRequestedClientId()),
                            request.getRedirectUriList(),
                            request.getScopeList(),
                            request.isConfidential(),
                            false)));
            applicationId = created.application().id();
        }

        Instant now = clock.instant();
        request.setApplicationId(applicationId);
        request.setStatus(AppConnectStatus.APPROVED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(now);
        request.setExpiresAt(now.plus(properties.redeemTtl()));
        String code = issueDeliveryCode(request);
        repository.save(request);

        auditService.record(
                AuditAction.APP_CONNECT_APPROVED, applicationId, request.getAppName(),
                "requestId=%s, origin=%s, reconnect=%s, product=%s".formatted(
                        request.getId(), origin(URI.create(request.getAppUrl())), request.isReconnect(), request.getProduct()));

        return callbackWith(request, "code", code);
    }

    @Transactional
    public String deny(UUID id, String reviewer) {
        AppConnectRequest request = requireReviewable(id);
        request.setStatus(AppConnectStatus.DENIED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(clock.instant());
        repository.save(request);
        auditService.record(
                AuditAction.APP_CONNECT_DENIED, request.getId().toString(), request.getAppName(),
                "origin=%s, product=%s".formatted(origin(URI.create(request.getAppUrl())), request.getProduct()));
        return callbackWith(request, "error", "access_denied");
    }

    /**
     * For an approved request whose code never reached the app (the approver's browser could not
     * reach an internal app, or the code expired): issues a new code. It is only ever sent to the
     * registered callback, so handing it to whoever asks is safe.
     */
    @Transactional
    public String resume(UUID id) {
        AppConnectRequest request = find(id);
        if (request.isExpired(clock.instant())) {
            throw new PortalException(ErrorCode.CONNECT_REQUEST_EXPIRED);
        }
        if (request.getStatus() != AppConnectStatus.APPROVED) {
            throw new PortalException(ErrorCode.CONNECT_REQUEST_NOT_REVIEWABLE);
        }
        String code = issueDeliveryCode(request);
        repository.save(request);
        return callbackWith(request, "code", code);
    }

    // ------------------------------------------------------------------ redeem (app backend)

    @Transactional(noRollbackFor = ConnectRedeemRejectedException.class)
    public ConnectRedeemResponse redeem(UUID id, String code, String codeVerifier) {
        requireEnabled();
        AppConnectRequest request = repository.findById(id).orElseThrow(ConnectRedeemRejectedException::new);
        Instant now = clock.instant();
        if (request.isExpired(now)) {
            throw new PortalException(ErrorCode.CONNECT_REQUEST_EXPIRED);
        }
        switch (request.getStatus()) {
            case PENDING -> throw new PortalException(ErrorCode.CONNECT_REQUEST_PENDING);
            case DENIED -> throw new PortalException(ErrorCode.CONNECT_REQUEST_DENIED);
            case REDEEMED, LOCKED -> throw new ConnectRedeemRejectedException();
            case APPROVED -> { }
        }

        boolean verifierOk = constantTimeEquals(s256(codeVerifier), request.getCodeChallenge());
        boolean codeOk = request.getDeliveryCodeHash() != null
                && request.getDeliveryCodeExpiresAt() != null
                && now.isBefore(request.getDeliveryCodeExpiresAt())
                && constantTimeEquals(sha256Hex(code), request.getDeliveryCodeHash());
        if (!verifierOk || !codeOk) {
            request.setFailedRedeemAttempts(request.getFailedRedeemAttempts() + 1);
            if (request.getFailedRedeemAttempts() >= properties.maxRedeemAttempts()) {
                request.setStatus(AppConnectStatus.LOCKED);
            }
            repository.save(request);
            throw new ConnectRedeemRejectedException();
        }

        if (repository.markRedeemed(id, now) != 1) {
            throw new ConnectRedeemRejectedException();
        }

        UUID applicationId = request.getApplicationId();
        String actor = "app-connect (approved by %s)".formatted(request.getReviewedBy());
        ApplicationCredentialsResponse credentials = runAs(actor, () -> {
            ApplicationCredentialsResponse issued = onboardingService.reissueCredentials(applicationId);
            auditService.record(
                    AuditAction.APP_CONNECT_REDEEMED, applicationId, request.getAppName(),
                    "requestId=%s, clientSecret=%s, apiKey=%s".formatted(
                            request.getId(),
                            issued.oidc() != null && issued.oidc().clientSecret() != null ? "issued" : "none",
                            issued.externalAuth() != null && issued.externalAuth().apiKey() != null ? "issued" : "none"));
            return issued;
        });

        String name = applicationRepository.findById(applicationId).map(Application::getName).orElse(request.getAppName());
        return new ConnectRedeemResponse(applicationId, name, credentials);
    }

    // ------------------------------------------------------------------ helpers

    @Transactional
    public int purgeExpired(Instant cutoff) {
        return repository.deleteExpiredBefore(cutoff);
    }

    private AppConnectRequest find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONNECT_REQUEST_NOT_FOUND, "Connection request not found."));
    }

    private AppConnectRequest requireReviewable(UUID id) {
        AppConnectRequest request = find(id);
        if (request.isExpired(clock.instant())) {
            throw new PortalException(ErrorCode.CONNECT_REQUEST_EXPIRED);
        }
        if (request.getStatus() != AppConnectStatus.PENDING) {
            throw new PortalException(ErrorCode.CONNECT_REQUEST_NOT_REVIEWABLE);
        }
        return request;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new PortalException(ErrorCode.CONNECT_DISABLED);
        }
    }

    /**
     * An existing application is the same app reinstalled only if it holds the requested client_id
     * <em>and</em> lives at the same origin. A matching client_id elsewhere is a different app that
     * happens to share a name, and gets a fresh client_id instead of taking over this one.
     */
    private Optional<Application> reconnectTarget(AppConnectRequest request) {
        String requestOrigin = origin(URI.create(request.getAppUrl()));
        return applicationRepository.findFirstByClientId(request.getRequestedClientId())
                .filter(app -> {
                    try {
                        return requestOrigin.equals(origin(new URI(app.getAppUrl())));
                    } catch (URISyntaxException | IllegalArgumentException e) {
                        return false;
                    }
                });
    }

    private String availableClientId(String requested) {
        if (oAuthClientRepository.findByClientId(requested).isEmpty()) {
            return requested;
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = truncate(requested, 90) + "-" + HexFormat.of().formatHex(randomBytes(3));
            if (oAuthClientRepository.findByClientId(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BusinessRuleViolationException(ErrorCode.CLIENT_ALREADY_EXISTS, "Could not allocate a unique client_id.");
    }

    private String issueDeliveryCode(AppConnectRequest request) {
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        request.setDeliveryCodeHash(sha256Hex(code));
        Instant codeExpiry = clock.instant().plus(properties.codeTtl());
        request.setDeliveryCodeExpiresAt(codeExpiry.isAfter(request.getExpiresAt()) ? request.getExpiresAt() : codeExpiry);
        return code;
    }

    private static String callbackWith(AppConnectRequest request, String param, String value) {
        return UriComponentsBuilder.fromUriString(request.getCallbackUri())
                .queryParam("request_id", request.getId())
                .queryParam(param, value)
                .build()
                .encode()
                .toUriString();
    }

    private String statusLabel(AppConnectRequest request) {
        if (request.isExpired(clock.instant()) && request.getStatus() != AppConnectStatus.REDEEMED) {
            return "expired";
        }
        return request.getStatus().name().toLowerCase(Locale.ROOT);
    }

    private URI parseAppUri(String value, String field) {
        URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException e) {
            throw invalid(field + " is not a valid URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw invalid(field + " must be an absolute URL with a host, and no credentials or fragment.");
        }
        boolean loopback = LOOPBACK_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
        if (!scheme.equals("https") && !(scheme.equals("http") && (loopback || properties.allowInsecureHttp()))) {
            throw invalid(field + " must use https:// (http:// is accepted only for localhost).");
        }
        return uri;
    }

    private String requireSameOrigin(String value, String origin, String field) {
        URI uri = parseAppUri(value, field);
        if (!origin(uri).equals(origin)) {
            throw invalid(field + " must be on the same origin as manifest.appUrl (" + origin + ").");
        }
        return uri.toString();
    }

    static String origin(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port == -1) {
            port = scheme.equals("https") ? 443 : 80;
        }
        boolean defaultPort = (scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80);
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
    }

    private static <T> T runAs(String actor, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    static String s256(String verifier) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
    }

    private static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return a != null && b != null
                && MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static PortalException invalid(String message) {
        return new BusinessRuleViolationException(ErrorCode.CONNECT_REQUEST_INVALID, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

}
