# Application Onboarding Guide

This is the short context file for work on adding or configuring apps in Portal SSO. Read this before changing `application/`, `client/`, or the Angular Applications and OAuth Clients screens.

## Mental Model

Portal SSO has two related but separate concepts:

- **Application**: a launchpad/catalog entry shown in the user portal. It has a name, launch URL, icon, category, enabled flag, access policy, and optional linked `clientId`.
- **OAuth Client**: the OIDC relying-party registration used by an app to sign users in. It has redirect URIs, scopes, client authentication method, PKCE settings, token settings, and optional confidential secret.

An app can be only a bookmark, only a catalog entry, or a catalog entry linked to an OAuth client. The linked field is `applications.client_id`, storing the OAuth client's public `client_id` string.

## Admin Flow

The preferred admin path is:

1. Go to **Applications**.
2. Click **New application**.
3. Fill the launchpad fields.
4. In **SSO registration**, either:
   - leave it as `None` for a standalone bookmark,
   - choose an existing OAuth client, or
   - enable **Create client** to create and link a new OAuth client during the same save.
5. Optionally enable **Allow external app login and registration API** when an app owner needs their
   own backend to call Portal SSO from a custom login/register UI.
6. **Unified Credentials Modal**: Upon saving, Portal SSO immediately displays all keys and configuration in one place:
   - Client ID
   - Client Secret (if confidential)
   - External API Key (`X-Portal-App-Key`)
   - Portal Issuer URL
   - Allowed Redirect URIs & Scopes
   - One-click `.env` copy snippet
7. Use **OAuth Clients** for advanced client registry work, edits, deletion, and token revocation.

The combined Applications flow is intentionally a convenience layer over the existing APIs. It must not remove or weaken the lower-level OAuth Client registry.

## Client App SDK Installation

Client apps can install and integrate Portal SSO with zero boilerplate:

- **Option 1 (Official NPM Package)**:
  ```bash
  npm install @tanmaysinghx/portal-sso
  ```
- **Option 2 (Direct from GitHub repository)**:
  ```bash
  npm install github:tanmaysinghx/portal-sso#path:packages/portal-sso
  ```
- **Option 3 (Direct CDN Script)**:
  ```html
  <script src="https://portal.tanmaysinghx.com/portal-sso.js"></script>
  ```

## External Login And Registration API

Some apps have their own login/register screens but want Portal SSO to remain the account source of
truth. For that case, enable external auth on the Application and generate an API key.

External API endpoints:

- `POST /api/external/apps/{applicationId}/register`
- `POST /api/external/apps/{applicationId}/login`

Required header:

- `X-Portal-App-Key: <one-time-generated-key>`

Important boundary: the API key is for the application backend only. Do not put it in browser
JavaScript, mobile apps, or any client bundle. A browser UI should call its own app backend; that
backend calls Portal SSO.

Current login behavior validates credentials and returns user details plus whether MFA is required.
It does not mint OAuth tokens, create a Portal SSO browser session, or bypass the normal OIDC flow.
For full SSO, continue to use the Authorization Code + PKCE flow.

## Backend Ownership

Application catalog:

- Controller: `portal-server/src/main/java/com/tanmaysinghx/portalsso/application/web/AdminApplicationController.java`
- Service: `portal-server/src/main/java/com/tanmaysinghx/portalsso/application/service/ApplicationService.java`
- Entity: `portal-server/src/main/java/com/tanmaysinghx/portalsso/application/entity/Application.java`
- Liquibase: `portal-server/src/main/resources/db/changelog/changes/012-create-applications-table.yaml`

OAuth client registry:

- Controller: `portal-server/src/main/java/com/tanmaysinghx/portalsso/client/web/AdminOAuthClientController.java`
- Registered client adapter: `portal-server/src/main/java/com/tanmaysinghx/portalsso/client/security/JpaRegisteredClientRepository.java`
- Entity: `portal-server/src/main/java/com/tanmaysinghx/portalsso/client/entity/OAuthClient.java`
- Liquibase: `portal-server/src/main/resources/db/changelog/changes/004-create-oauth-clients-table.yaml`

## Frontend Ownership

- Applications admin page: `portal-client/src/app/features/applications/pages/application-list/`
- Application API service/model: `portal-client/src/app/features/applications/services/application.service.ts`, `portal-client/src/app/features/applications/models/application.model.ts`
- OAuth client API service/model: `portal-client/src/app/features/clients/services/oauth-client.service.ts`, `portal-client/src/app/features/clients/models/oauth-client.model.ts`
- Standalone OAuth client create page: `portal-client/src/app/features/clients/pages/client-create/`

## Security Invariants

- PKCE stays required for every authorization-code client, including confidential clients.
- Confidential client secrets are generated server-side, returned once, and stored only as a hash.
- External app API keys are generated server-side, returned once, and stored only as a hash.
- Never log or persist raw secrets in `audit_events`, `login_events`, browser storage, or application records.
- Redirect URI changes are security-sensitive and must remain audited through the OAuth client API.
- Deleting an OAuth client must continue to revoke related grants and consents.
- Deleting an Application removes only the launchpad/catalog entry; it must not silently delete the linked OAuth client.
- External login/register API keys must not grant admin API access and must only work for their own Application.

## Testing Checklist

For changes in this area, run at least:

```bash
cd portal-server
cmd /c .\mvnw test "-Dtest=ApplicationControllerIntegrationTest,AdminOAuthClientControllerTest,ConfidentialClientTest"
```

For frontend form changes:

```bash
cd portal-client
npm run build
```

Use `npm.cmd` in PowerShell if script execution policy blocks `npm`.
