# Portal SSO — Integration Brief for Coding Agents

> **How to use:** give this file to a coding agent (Claude Code, Cursor, Copilot, Codex…) in the
> repository of the app you want to connect, with the prompt:
>
> *"Integrate Portal SSO sign-in into this repo following AI_AGENT_INTEGRATION.md. Ask me for any
> value in the Facts table you can't find."*
>
> For a specific app, prefer the generated `PORTAL_SSO.md` (Portal admin → **Applications →
> Agent guide**). It is this brief with the Facts already filled in from the real registration.
> Neither file contains secrets.

---

## 0. Facts (fill these in; never guess them)

| Setting | Value | Where to get it |
|---|---|---|
| `PORTAL_URL` (issuer) | `https://sso.example.com` | Keys & setup → Issuer URL |
| `PORTAL_CLIENT_ID` | `my-app` | Keys & setup → Client ID |
| Client type | public / confidential | Keys & setup → OAuth client badge |
| Registered redirect URIs | `https://my-app.example.com`, `{PORTAL_URL}/oauth2/popup-callback` | Keys & setup → Registered redirect URIs |
| Scopes | `openid profile email` | Keys & setup → Scopes |
| External API enabled? | yes / no | Keys & setup → External login/register API |
| Application ID (external API only) | UUID | Part of the login endpoint URL |

Secrets are **never** in this file. They come from the app's secret store or `.env`:

| Env var | When needed | Who may read it |
|---|---|---|
| `PORTAL_CLIENT_SECRET` | Confidential client only | Server-side code only |
| `PORTAL_API_KEY` | External API only | Server-side code only |

---

## 1. Rules you must follow

1. **PKCE S256 is mandatory on every client**, public or confidential. Always send
   `code_challenge` + `code_challenge_method=S256`, and the `code_verifier` at token exchange.
2. **`redirect_uri` must exactly match a registered URI**: scheme, host, port, path and
   trailing slash. If the app needs a new one, stop and ask the human to add it in Portal SSO
   (**OAuth Clients → edit**). Never work around a mismatch.
3. **Read every value from environment variables.** Never hard-code secrets, never commit `.env`.
   Add a `.env.example` that lists names only.
4. **Secrets stay server-side.** `PORTAL_CLIENT_SECRET` and `PORTAL_API_KEY` must never appear in
   browser JavaScript, mobile bundles, logs, or error messages.
5. **Verify tokens properly.** Validate JWT signatures against `{PORTAL_URL}/oauth2/jwks` (RS256)
   and check `iss == PORTAL_URL` and `aud` contains `PORTAL_CLIENT_ID`. Never trust a decoded but
   unverified JWT.
6. Prefer the official SDK (`@tanmaysinghx/portal-sso`) for browser apps and a standard,
   maintained OIDC library for server apps. Do not hand-roll OAuth.

---

## 2. Pick the integration path

| The app is… | Use | Section |
|---|---|---|
| A browser SPA (React, Angular, Vue, Svelte, plain HTML) | SDK popup or redirect login, public client | §3 |
| A server-rendered web app (Spring, Express, Django, FastAPI, Rails, Go) | Standard OIDC auth-code + PKCE, usually a confidential client | §4 |
| An API that receives Portal access tokens from a frontend | JWT bearer validation | §5 |
| An app with its **own** login/register UI that needs Portal accounts | External API from the backend | §6 |
| A self-hosted product that should set itself up with no copied secrets | "Connect with Portal SSO" (manifest → admin approval → code + verifier redeem) | [APP_CONNECT.md](APP_CONNECT.md) |

Endpoints (all under `PORTAL_URL`): discovery `/.well-known/openid-configuration`, authorize
`/oauth2/authorize`, token `/oauth2/token`, JWKS `/oauth2/jwks`, userinfo `/userinfo`, popup relay
`/oauth2/popup-callback`.

---

## 3. Browser SPA: official SDK

```bash
npm install @tanmaysinghx/portal-sso
# or: <script src="{PORTAL_URL}/portal-sso.js"></script>  (global PortalSSO)
```

```ts
// src/auth/portal.ts: one shared instance
import { PortalSSO } from '@tanmaysinghx/portal-sso';

export const sso = new PortalSSO({
  portalUrl: import.meta.env.VITE_PORTAL_URL,        // adapt to the framework's env mechanism
  clientId: import.meta.env.VITE_PORTAL_CLIENT_ID,
  // redirectUri defaults to `${portalUrl}/oauth2/popup-callback` (always registered)
  // scope defaults to 'openid profile email'
});
```

| Call | Result |
|---|---|
| `await sso.loginWithPopup()` | `{ user, accessToken, idToken, refreshToken?, expiresAt }`. The user is `{ id, email, name, roles }`. |
| `sso.getCurrentUser()` | Cached `PortalUser` or `null`. |
| `sso.logout()` | Clears the local session. |
| `await sso.loginWithRedirect(uri?)` + `await sso.handleRedirectCallback()` | Full-page redirect flow. `uri` must be registered; the default is the current URL. |

Drop-in button: `import '@tanmaysinghx/portal-sso'`, then
`<portal-login-button portal-url="…" client-id="…"></portal-login-button>`, and listen for the
`login-success` event (`e.detail.user`).

Before API calls, get a current token with `const accessToken = await sso.getAccessTokenAsync()` and
send `Authorization: Bearer ${accessToken}` to the app's own API. The SDK refreshes the access token
before expiry and stores the rotated refresh token. Validate the access token at the app's API (§5).

---

## 4. Server-side web app: standard OIDC

Configure any certified OIDC client with:

- issuer / discovery: `PORTAL_URL`
- `client_id`: `PORTAL_CLIENT_ID`
- client auth: `client_secret_basic` (or `client_secret_post`) with `PORTAL_CLIENT_SECRET` for confidential clients, or `none` for public ones
- response type `code`, **PKCE S256 on**, scopes `PORTAL_SCOPES`
- `redirect_uri`: `PORTAL_REDIRECT_URI` (must be registered)

**Spring Boot** (`application.yml`):

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          portal:
            client-id: ${PORTAL_CLIENT_ID}
            client-secret: ${PORTAL_CLIENT_SECRET:}
            scope: openid,profile,email
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"   # register this exact URI
        provider:
          portal:
            issuer-uri: ${PORTAL_URL}
```

Check that the authorization request carries `code_challenge`. If it doesn't (some Spring
Security versions skip PKCE for confidential clients), set
`OAuth2AuthorizationRequestCustomizers.withPkce()` on the `DefaultOAuth2AuthorizationRequestResolver`.

**Node** (`openid-client` v5):

```js
const { Issuer, generators } = require('openid-client');
const issuer = await Issuer.discover(process.env.PORTAL_URL);
const client = new issuer.Client({
  client_id: process.env.PORTAL_CLIENT_ID,
  client_secret: process.env.PORTAL_CLIENT_SECRET,       // omit for public clients
  redirect_uris: [process.env.PORTAL_REDIRECT_URI],
  response_types: ['code'],
  token_endpoint_auth_method: process.env.PORTAL_CLIENT_SECRET ? 'client_secret_basic' : 'none',
});
const code_verifier = generators.codeVerifier();          // store in the session
const url = client.authorizationUrl({
  scope: process.env.PORTAL_SCOPES,
  code_challenge: generators.codeChallenge(code_verifier),
  code_challenge_method: 'S256',
});
// callback: client.callback(process.env.PORTAL_REDIRECT_URI, client.callbackParams(req), { code_verifier })
```

**Python** (Authlib):

```python
oauth.register(
    name="portal",
    server_metadata_url=f"{os.environ['PORTAL_URL']}/.well-known/openid-configuration",
    client_id=os.environ["PORTAL_CLIENT_ID"],
    client_secret=os.environ.get("PORTAL_CLIENT_SECRET"),
    client_kwargs={"scope": os.environ.get("PORTAL_SCOPES", "openid profile email"),
                   "code_challenge_method": "S256"},
)
```

**Legacy apps with no OIDC library** (JSP, PHP, Tomcat): put `oauth2-proxy` in front with
`provider=oidc`, `oidc_issuer_url=$PORTAL_URL`, `code_challenge_method=S256`, and read the
`X-Forwarded-Email` / `X-Forwarded-User` headers.

---

## 5. API backend: validate bearer tokens

**Spring Boot**: `spring.security.oauth2.resourceserver.jwt.issuer-uri: ${PORTAL_URL}`.

**Node** (`jose`):

```js
import { createRemoteJWKSet, jwtVerify } from 'jose';
const JWKS = createRemoteJWKSet(new URL(`${process.env.PORTAL_URL}/oauth2/jwks`));
const { payload } = await jwtVerify(token, JWKS, { issuer: process.env.PORTAL_URL });
```

**Python** (`PyJWT`):

```python
jwks = jwt.PyJWKClient(f"{PORTAL_URL}/oauth2/jwks")
claims = jwt.decode(token, jwks.get_signing_key_from_jwt(token).key,
                    algorithms=["RS256"], issuer=PORTAL_URL, audience=PORTAL_CLIENT_ID)
```

Access tokens live 15 minutes and refresh tokens 30 days. Refresh tokens rotate on every use and
are never reused.

---

## 6. External login/register API (backend only)

For apps that keep their own login form but use Portal SSO as the account store. **The browser
calls the app's backend; only the backend calls Portal.**

Every request needs `X-Portal-App-Key: $PORTAL_API_KEY` and `Content-Type: application/json`. No
CSRF token or cookies are involved.

| Call | Body | Success |
|---|---|---|
| `POST {PORTAL_URL}/api/external/apps/{applicationId}/login` | `{ "email", "password" }` | `200` |
| `POST {PORTAL_URL}/api/external/apps/{applicationId}/register` | `{ "email", "password", "firstName", "lastName" }` | `201` (only if registration is enabled) |

Response:

```json
{ "authenticated": true, "mfaRequired": false,
  "user": { "id": "uuid", "email": "a@b.com", "firstName": "A", "lastName": "B", "roles": ["ROLE_USER"] } }
```

Errors use `{ "status", "code", "message", "path" }`:

| HTTP | Code | Meaning |
|---|---|---|
| 401 | `PRTL-1001` | Wrong or missing API key |
| 401 | `PRTL-1003` | Wrong email or password |
| 403 | `PRTL-1002` | App disabled, external API off, or no key issued |
| 403 | `PRTL-1004` / `PRTL-1005` | Account locked / disabled |
| 403 | `PRTL-3009` | Registration disabled for this app |
| 409 | `PRTL-2003` | Email already registered |

This API **verifies credentials only**. It mints no OAuth tokens and no Portal session; the
app manages its own session. If `mfaRequired` is true, send the user through the OIDC flow
(§3/§4) instead. For real single sign-on, always prefer §3/§4.

---

## 7. Troubleshooting

| Error | Fix |
|---|---|
| Portal shows a `redirect_uri` error | The URI sent doesn't exactly match a registered one. Compare character by character, then ask the admin to register the exact URI. |
| `invalid_request: code_challenge` | Enable PKCE S256 in the client library. |
| `invalid_client` at the token endpoint | Wrong secret or wrong auth method. Public clients must use `none`; confidential clients use `client_secret_basic`/`post`. |
| Token rejected: issuer mismatch | `PORTAL_URL` must equal the token's `iss` exactly, with no trailing slash. |
| Popup closes but nothing happens | Popup blocked, or `portalUrl` points somewhere else. Call `loginWithPopup()` directly from a click handler. |
| CORS error calling the external API from the browser | That's expected: it's backend only. Proxy the call through the app's server. |

---

## 8. Done checklist

- [ ] All Portal values come from env vars; `.env` is git-ignored; `.env.example` lists names only.
- [ ] Sign-in completes end to end against `PORTAL_URL`.
- [ ] PKCE S256 is sent on every authorization request.
- [ ] Backend validates token signature, issuer and expiry before trusting claims.
- [ ] No secret in client bundles, logs, test fixtures or git history.
- [ ] README of the app documents the required env vars.
