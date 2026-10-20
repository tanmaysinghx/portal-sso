# Portal SSO — Working Roadmap

The tracker we work from, one feature at a time. `docs/FEATURES.md` is the older public matrix
and is out of date; this file is the one to trust and to update.

**How to use it**
- Pick one item from **Up next**, move it to **In progress**, build it (backend + UI + Liquibase
  changeset + tests + audit entries), then move it to **Implemented** with a one-line note.
- Every item must respect the invariants in `AGENTS.md`: H2/Postgres/MySQL portable, PKCE always,
  no plaintext secrets, audit records field names but never values, mail through `MailService`.
- Size: **S** ≈ a session, **M** ≈ a few sessions, **L** = needs a design note first.

_Last reviewed: 2026-10-19 (against `main` @ `b47ba01` + forgot-password / expiry work)_

---

## ✅ Implemented

### Protocol & tokens
- OAuth 2.1 Authorization Code + **mandatory S256 PKCE** for public *and* confidential clients
- Refresh tokens with rotation (no reuse); access 15 min / refresh 30 days (hard-coded per client)
- OIDC discovery, JWKS, UserInfo, token revocation; roles customised into JWT claims
- Optional per-client consent screen (`/oauth2/consent`)
- Confidential clients (`client_secret_basic` / `_post`) with secret regeneration
- DB-persisted RSA-2048 signing keys (generated on first boot)
- Popup login flow (`/oauth2/popup-callback`, `docs/POPUP_LOGIN_QUICKSTART.md`)
- Branded OAuth error page

### Authentication
- Username/password sign-in, configurable password policy (length, character classes)
- Account lockout after N failures + admin unlock
- TOTP MFA (AES-256-GCM secrets, encryption-key rotation via `previous-encryption-key`) + recovery codes; admin MFA reset
- Magic-link passwordless sign-in & sign-up (MFA still enforced, no enumeration)
- **Forgot password** (`/forgot-password` → emailed link → `/reset-password`): same token rules as magic links (shared `OpaqueTokens`), identical answer for every address, link stripped from the URL bar, reset clears lockout and signs out every session, never signs in (MFA still asked at next sign-in); offered only while a relay is configured
- **Password expiry** (`app.security.password.max-age-days`, default 90, 0 = off): countdown on My Account + console-wide banner inside the warning window (`expiry-warning-days`, 14); an expired password is changed at `/password-expired` right after the password step, before MFA
- Remember-me is issued only after a sign-in's second step completes (it used to be minted before the MFA challenge, so the cookie alone bypassed MFA); MFA completion now rotates the session id
- Console sign-in follows MFA / expired-password steps (`GET /api/public/sign-in/pending`) instead of reporting "invalid password"
- New-device detection (browser + OS family) with alert email
- Public self-registration (toggleable policy)
- Per-path token-bucket rate limiting

### Applications & integration
- Application catalog / user launchpad (`/apps`)
- One-transaction onboarding (`POST /api/admin/applications/onboard`): client + app + API key
- External app login/register API secured by hashed per-app API keys
- "Connect with Portal SSO": manifest → admin approval → two-proof redeem
- Access requests (user requests app access, admin approves/denies)
- Generated `.env` / redirect URI / agent-guide snippets (`integration-snippets.ts`)

### Users, roles & sessions
- **My Account** (`/account`, `/api/user/account`): edit name, change password (current password required, other sessions signed out, session id rotated), 2FA enrol/disable for *every* user, regenerate recovery codes (proved with an authenticator code), list/forget recognised devices; all audited
- User CRUD, enable/disable, role assignment, CSV bulk import
- Custom roles CRUD (platform roles protected), last-admin/self-demotion guards
- Spring Session JDBC (cluster-safe); admin view/revoke of a user's sessions; user self-service sessions page with "sign out others"

### Governance & analytics
- Append-only audit log with filters + CSV export
- Login events with GeoIP, dashboard KPIs/map/recency, hourly retention purge (48 h default)

### Mail
- DB-stored SMTP relay (encrypted password, test send, audited changes)
- Thymeleaf HTML + text templates: welcome, new-device, sign-in notice, magic links; branding & per-message switches

### Operations
- Single JAR with embedded Angular SPA; Docker image + Compose; `install.sh`
- Zero-config H2 boot, initial-admin secret file, first-run setup wizard
- In-app H2 → PostgreSQL/MySQL migration wizard with restart
- Light/dark theme, console branding, product / docs / download pages
- Settings split into grouped sub-routes (`/settings/branding`, `/email-delivery`, `/email-templates`, `/protocol`, `/database`, `/system`); old `?tab=` links redirect
- GitHub Actions CI + release workflows

---

## 🚧 In progress

_(nothing yet)_

---

## 🎯 Up next — P0 (gaps every real IdP is expected to close)

| # | Feature | Size | Notes |
|---|---|---|---|
| 0 | **🐞 Embedded H2 fails on second start** | S–M | Pre-existing on `main` (reproduced on `b47ba01`): a fresh `~/.portal-sso` boots, every later boot dies in Liquibase with `Table "databasechangelog" already exists`. Liquibase 5.0.3 + H2 2.4.240 + `DATABASE_TO_LOWER=TRUE`; lower-case table names, `default-schema=public` and `preserveSchemaCase` did not fix it. Fix first — it breaks the "just run the JAR" promise. |
| 3 | **Email verification + admin invitations** | M | Verify the address on self-sign-up; admin "Invite user" sends a set-password link instead of an admin choosing a password. Adds `email_verified` claim. |
| 4 | **Signing-key rotation & retirement** | M | Admin "Rotate now" + optional scheduled rotation; old keys stay in JWKS for a grace period, then retire. Today keys are only generated on first boot. |
| 5 | **OIDC logout, done properly** | S–M | Confirm RP-initiated logout (`end_session_endpoint`) works with stored `post_logout_redirect_uris`, add them to onboarding UI/snippets, then add back-channel logout to notify apps. |
| 6 | **Health & metrics** | S | Actuator `health`/`readiness`/`liveness` for Docker/K8s, optional Prometheus endpoint (sign-ins, failures, token issuance) behind admin auth or a separate port. |

## 🔭 P1 — strong differentiators

| # | Feature | Size | Notes |
|---|---|---|---|
| 7 | **Passkeys / WebAuthn** | L | Spring Security ships WebAuthn support; passkey as primary login or as second factor. Credentials table must be portable (`VARCHAR`/`BLOB`). |
| 8 | **Machine-to-machine: `client_credentials` + token introspection** | M | Service accounts for backend APIs; RFC 7662 introspection for opaque-token consumers. |
| 9 | **Per-client token & claim settings** | M | Editable access/refresh TTLs, scopes, custom claim mappings, app-scoped roles in the token. |
| 10 | **Breached-password check** | S | Opt-in HIBP k-anonymity range lookup (only a 5-char SHA-1 prefix leaves the server). Hook already anticipated in `PasswordPolicyProperties`. |
| 11 | **Webhooks / event stream** | M | Signed (HMAC) outbound events: `user.created`, `user.disabled`, `login.failed`, `mfa.reset`… with retry + delivery log. |
| 12 | **Groups** | M | Assign users to groups, grant app access and roles by group; groups claim in tokens. |
| 13 | **Tamper-evident audit + SIEM export** | M | Hash-chain `audit_events` rows; JSON/syslog streaming export. |
| 14 | **Session & step-up policies** | M | Idle/absolute session timeout settings, max concurrent sessions, re-authentication (password/MFA) before sensitive admin actions. |
| 15 | **Enforce MFA by policy** | S | Require MFA for admins / for everyone / per application, with grace-period enrolment. |

## 🌐 P2 — enterprise reach

| # | Feature | Size | Notes |
|---|---|---|---|
| 16 | **Social / upstream IdP login** | L | Google, GitHub, Microsoft, generic OIDC; account linking by verified email only. |
| 17 | **LDAP / Active Directory** | L | Bind authentication + optional user sync. |
| 18 | **SCIM 2.0 provisioning** | L | Inbound (from HR/Okta) and outbound to apps. |
| 19 | **SAML 2.0 IdP** | L | For legacy enterprise apps that cannot speak OIDC. |
| 20 | **Delegated admin permissions** | M | Fine-grained permissions (e.g. help-desk: unlock + MFA reset only) instead of all-or-nothing `ROLE_ADMIN`. |
| 21 | **Device authorization grant (RFC 8628)** | M | Sign-in for CLIs and TVs; tables already exist in the auth schema. |
| 22 | **Backup & restore** | M | Encrypted export of config + keys, with a reminder to back up the at-rest encryption key. |
| 23 | **Internationalisation** | M | Angular i18n + translated email templates and login pages. |
| 24 | **Go reverse-proxy sidecar** (`portal-sso-proxy`) | L | Zero-code protection for apps that can't integrate OIDC. |

---

## 🧹 Housekeeping (pick up alongside features)
- Follow-ups from My Account / reset: "your password was changed" security email (new template + switch); revoke the user's OAuth refresh tokens on password change and reset; email reminder N days before expiry (today the warning is in the console only).
- Password expiry is enforced at the password step; a sign-in by magic link or a still-valid remember-me cookie is not stopped by an expired password (both are shown the banner).
- Rewrite `docs/FEATURES.md` from this file (its "Planned" rows are mostly already shipped).
- Frontend test coverage is thin (4 spec files vs 56 backend test classes).
