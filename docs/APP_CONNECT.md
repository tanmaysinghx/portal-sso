# Connect with Portal SSO

Lets an app register itself with Portal SSO and receive its credentials **without anyone copying
a secret**. A Portal administrator approves each connection. Eggless Observability is the first
app that uses it; any app can.

Read [APP_ONBOARDING.md](APP_ONBOARDING.md) first. Connect is a front door to the same onboarding
transaction, not a separate registry.

---

## 1. Flow

```
App backend                            Portal SSO                              Portal admin (browser)
───────────                            ──────────                              ──────────────────────
1. POST /api/external/connect/requests
   {manifest, callbackUri,
    codeChallenge = S256(verifier)}  ─► stores PENDING request (24h)
   ◄── {requestId, approvalUrl}

2. sends operator's browser to approvalUrl ─────────────────────────────────►  GET /connect/{id}
                                                                               (sign-in required)
                                       ◄──────────────────────────── POST /connect/{id}/approve (ADMIN + CSRF)
                                       onboard(): app + OAuth client (+ API key)
                                       status APPROVED (1h to redeem)
3.                                     302 → callbackUri?request_id=…&code=…  (code: one-time, 10 min)

4. POST /api/external/connect/requests/{id}/redeem
   {code, codeVerifier}              ─► checks both, flips APPROVED→REDEEMED once,
                                         generates fresh client secret + API key
   ◄── {applicationId, credentials}      (only response that ever carries them)
```

Non-admins who open the approval link see "an administrator must approve". Pending requests are
listed for admins at the top of **Applications** (`GET /api/admin/connect-requests`).

If the approver's browser cannot reach the app (internal hostname), the operator reopens the
approval link after approval and clicks **Continue** (`POST /connect/{id}/resume`), which issues a
new code to the same callback.

## 2. Why two proofs

| Proof | Defends against |
|---|---|
| **Delivery code**, sent only to `callbackUri` | Someone submitting a request that *claims* another app's address, waiting for an admin to approve it, then redeeming. Without the code they'd collect that app's rotated secrets. The code only ever reaches the real address. |
| **Verifier** (PKCE-style S256) | A code leaked from a URL, proxy log or browser history. Useless without the verifier held by the backend that submitted the request. |

## 3. Security rules

- `callbackUri`, every `redirectUris` entry and `iconUrl` must share the origin of `manifest.appUrl`.
- `https://` only, except loopback hosts (`localhost`, `127.0.0.1`, `[::1]`). `APP_CONNECT_ALLOW_INSECURE_HTTP=true` relaxes it; the approval page then warns.
- Approve/deny require `ROLE_ADMIN` and CSRF. The form carries only the admin's choices; everything else is reread from the stored request.
- The admin may withhold the external auth API (password checking) and registration, even when requested. Registration is unticked by default.
- `product` (for example `eggless-observability`) only picks a label and category (`connect/KnownProducts.java`). It is **not** identity: the page says the address is what is being approved.
- No plaintext secret is ever stored on the request. Secrets made during approval are discarded and replaced at redeem (`ApplicationOnboardingService.reissueCredentials`).
- Redeem is single-use (compare-and-set). Five wrong code/verifier pairs lock the request.
- `/api/external/connect/` is rate limited (10/min per IP), at most 50 unexpired pending requests, and the hourly purge deletes requests a week after expiry.
- Audit: `APP_CONNECT_APPROVED`, `APP_CONNECT_DENIED`, `APP_CONNECT_REDEEMED`. The redeem's actor is `app-connect (approved by <admin>)`.

## 4. Reinstall / reconnect

A new request with the **same `client_id` at the same origin** as an existing application is
treated as a reinstall. Approval creates nothing and keeps that app's settings, and redeem rotates its
credentials, so any other running copy stops working. The page warns about this. The same `client_id`
at a *different* origin gets a fresh `client_id` with a random suffix and never takes over the
existing one.

## 5. API

| Method & path | Auth | Purpose |
|---|---|---|
| `POST /api/external/connect/requests` | none (rate limited) | Submit manifest. `201 {requestId, approvalUrl, expiresAt}` |
| `GET /api/external/connect/requests/{id}` | none | `{status: pending\|approved\|denied\|redeemed\|locked\|expired}` |
| `POST /api/external/connect/requests/{id}/redeem` | code + verifier | Credentials, once. `Cache-Control: no-store` |
| `GET /connect/{id}` | signed in | Approval page |
| `POST /connect/{id}/approve` · `/deny` | ADMIN + CSRF | Decide; redirects to the callback |
| `POST /connect/{id}/resume` | signed in + CSRF | New code for an approved request |
| `GET /api/admin/connect-requests` | ADMIN | Pending requests for the console |

Manifest fields: `product`, `name`*, `description`, `appUrl`*, `iconUrl`, `clientId` (defaults to
`product`, then a slug of `name`), `redirectUris` (defaults to `appUrl`; the popup callback is added
automatically), `scopes` (default `openid profile email`), `confidential` (default true),
`externalAuth`, `externalRegistration`.

Errors: `PRTL-3010` disabled · `3011` not found · `3012` invalid manifest/URL · `3013` expired ·
`3014` already decided · `3015` still pending · `3016` denied · `3017` bad code/verifier or used ·
`3018` too many pending.

## 6. Configuration

```yaml
app.connect:
  enabled: true            # APP_CONNECT_ENABLED
  pending-ttl: 24h
  redeem-ttl: 1h
  code-ttl: 10m
  max-pending: 50
  max-redeem-attempts: 5
  allow-insecure-http: false   # APP_CONNECT_ALLOW_INSECURE_HTTP
```

## 7. Code

| Concern | File |
|---|---|
| Flow, validation, proofs | `connect/service/AppConnectService.java` |
| Approval page | `connect/web/AppConnectPageController.java`, `templates/connect.html` |
| App-backend endpoints | `connect/web/ExternalAppConnectController.java` |
| Console listing | `connect/web/AdminAppConnectController.java`, Applications page banner |
| Known ecosystem products | `connect/KnownProducts.java` |
| Schema | `db/changelog/changes/016-create-app-connect-requests-table.yaml` |
| Tests | `connect/web/AppConnectFlowIntegrationTest.java` |
