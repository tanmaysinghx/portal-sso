package com.tanmaysinghx.portalsso.application.service;

import com.tanmaysinghx.portalsso.application.entity.Application;
import com.tanmaysinghx.portalsso.application.repository.ApplicationRepository;
import com.tanmaysinghx.portalsso.application.web.dto.ApplicationCredentialsResponse;
import com.tanmaysinghx.portalsso.application.web.dto.ApplicationOnboardingResponse;
import com.tanmaysinghx.portalsso.application.web.dto.ApplicationResponse;
import com.tanmaysinghx.portalsso.application.web.dto.CreateApplicationRequest;
import com.tanmaysinghx.portalsso.application.web.dto.OnboardApplicationRequest;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.client.service.OAuthClientRegistrationService;
import com.tanmaysinghx.portalsso.client.web.dto.CreateOAuthClientRequest;
import com.tanmaysinghx.portalsso.client.web.dto.OAuthClientCreatedResponse;
import com.tanmaysinghx.portalsso.client.web.dto.OAuthClientResponse;
import com.tanmaysinghx.portalsso.common.error.BusinessRuleViolationException;
import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one-step "add an application" flow and the single source of truth for an application's
 * integration settings.
 *
 * <p>Onboarding creates the OAuth client, the catalog entry and the external API key inside one
 * transaction. Before this existed the admin UI made three separate calls, so a failure in the
 * second left an orphaned OAuth client behind and the retry then failed with "client_id already
 * exists".
 */
@Service
public class ApplicationOnboardingService {

    /** Served by {@code AuthPageController}; the SDK's {@code loginWithPopup()} uses it by default. */
    public static final String POPUP_CALLBACK_PATH = "/oauth2/popup-callback";

    private static final List<String> DEFAULT_SCOPES = List.of("openid", "profile", "email");

    private final ApplicationService applicationService;
    private final ApplicationRepository applicationRepository;
    private final OAuthClientRegistrationService clientRegistrationService;
    private final OAuthClientRepository oAuthClientRepository;
    private final String issuerUrl;

    public ApplicationOnboardingService(
            ApplicationService applicationService,
            ApplicationRepository applicationRepository,
            OAuthClientRegistrationService clientRegistrationService,
            OAuthClientRepository oAuthClientRepository,
            @Value("${spring.security.oauth2.authorizationserver.issuer:http://localhost:8080}") String issuerUrl) {
        this.applicationService = applicationService;
        this.applicationRepository = applicationRepository;
        this.clientRegistrationService = clientRegistrationService;
        this.oAuthClientRepository = oAuthClientRepository;
        this.issuerUrl = stripTrailingSlash(issuerUrl);
    }

    @Transactional
    public ApplicationOnboardingResponse onboard(OnboardApplicationRequest request) {
        CreateApplicationRequest app = request.application();
        OnboardApplicationRequest.NewOAuthClient newClient = request.oauthClient();

        if (newClient != null && app.clientId() != null && !app.clientId().isBlank()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.INVALID_CLIENT_METADATA,
                    "Either link an existing client (application.clientId) or create a new one (oauthClient), not both.");
        }

        String clientSecret = null;
        if (newClient != null) {
            OAuthClientCreatedResponse created = clientRegistrationService.create(toClientRequest(app, newClient));
            clientSecret = created.clientSecret();
            app = withClientId(app, created.client().clientId());
        }

        ApplicationResponse saved = applicationService.create(app);

        String apiKey = null;
        if (saved.externalAuthEnabled()) {
            apiKey = applicationService.rotateExternalAuthApiKey(saved.id());
            saved = applicationService.getById(saved.id());
        }

        return new ApplicationOnboardingResponse(saved, credentialsFor(saved.id(), clientSecret, apiKey));
    }

    /** The integration settings for an existing application. Never contains secrets. */
    @Transactional(readOnly = true)
    public ApplicationCredentialsResponse credentials(UUID applicationId) {
        return credentialsFor(applicationId, null, null);
    }

    /** Regenerates the linked confidential client's secret; the bundle carries the new value once. */
    @Transactional
    public ApplicationCredentialsResponse regenerateClientSecret(UUID applicationId) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found: " + applicationId));
        if (app.getClientId() == null) {
            throw new BusinessRuleViolationException(ErrorCode.CLIENT_NOT_FOUND, "This application is not linked to an OAuth client.");
        }
        String secret = clientRegistrationService.regenerateSecret(app.getClientId()).clientSecret();
        return credentialsFor(applicationId, secret, null);
    }

    private ApplicationCredentialsResponse credentialsFor(UUID applicationId, String clientSecret, String apiKey) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found: " + applicationId));

        ApplicationCredentialsResponse.Oidc oidc = null;
        if (app.getClientId() != null) {
            OAuthClientResponse client = oAuthClientRepository.findByClientId(app.getClientId())
                    .map(OAuthClientResponse::from)
                    .orElse(null);
            oidc = new ApplicationCredentialsResponse.Oidc(
                    app.getClientId(),
                    clientSecret,
                    client != null && client.confidential(),
                    client != null ? client.redirectUris() : List.of(),
                    client != null ? client.scopes() : List.of(),
                    issuerUrl + POPUP_CALLBACK_PATH,
                    client != null);
        }

        ApplicationCredentialsResponse.ExternalAuth externalAuth = null;
        if (app.isExternalAuthEnabled()) {
            String base = issuerUrl + "/api/external/apps/" + app.getId();
            externalAuth = new ApplicationCredentialsResponse.ExternalAuth(
                    ExternalApplicationAuthService.API_KEY_HEADER,
                    apiKey,
                    app.getExternalAuthApiKeyPrefix(),
                    app.getExternalAuthApiKeyHash() != null,
                    app.isExternalRegistrationEnabled(),
                    base + "/login",
                    app.isExternalRegistrationEnabled() ? base + "/register" : null);
        }

        return new ApplicationCredentialsResponse(
                issuerUrl, issuerUrl + "/.well-known/openid-configuration", oidc, externalAuth);
    }

    private CreateOAuthClientRequest toClientRequest(CreateApplicationRequest app, OnboardApplicationRequest.NewOAuthClient client) {
        String clientId = client.clientId() != null && !client.clientId().isBlank()
                ? client.clientId().trim()
                : toClientId(app.name());
        if (clientId.isEmpty()) {
            throw new BusinessRuleViolationException(
                    ErrorCode.INVALID_CLIENT_METADATA,
                    "Could not derive a client_id from the application name; provide oauthClient.clientId.");
        }

        // The popup callback is always allowed so the SDK's loginWithPopup() works out of the box.
        Set<String> redirectUris = new LinkedHashSet<>(clean(client.redirectUris()));
        if (redirectUris.isEmpty()) {
            redirectUris.add(app.appUrl().trim());
        }
        redirectUris.add(issuerUrl + POPUP_CALLBACK_PATH);

        Set<String> scopes = new LinkedHashSet<>();
        scopes.add("openid");
        scopes.addAll(client.scopes() == null || client.scopes().isEmpty() ? DEFAULT_SCOPES : clean(client.scopes()));

        return new CreateOAuthClientRequest(
                clientId,
                app.name().trim(),
                new ArrayList<>(redirectUris),
                new ArrayList<>(scopes),
                app.iconUrl(),
                client.requireConsent(),
                client.confidential());
    }

    private static CreateApplicationRequest withClientId(CreateApplicationRequest r, String clientId) {
        return new CreateApplicationRequest(
                r.name(), r.description(), r.appUrl(), r.iconUrl(), r.category(), clientId,
                r.accessType(), r.roleIds(), r.enabled(), r.displayOrder(),
                r.externalAuthEnabled(), r.externalRegistrationEnabled());
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
    }

    static String toClientId(String name) {
        String slug = name == null ? "" : name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        return slug.length() > 80 ? slug.substring(0, 80) : slug;
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
