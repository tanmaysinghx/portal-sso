# Portal SSO — Feature Matrix & Roadmap

Comprehensive status of implemented, in-progress, and planned features for the Portal SSO platform.

---

## 🚀 Feature Matrix

| Feature / Capability | Category | Status | Notes / Reference |
|---|---|---|---|
| **OAuth 2.1 & PKCE Flow** | Core Auth | ✅ **Completed** | Full Authorization Code + S256 PKCE grant |
| **Refresh Token Grant** | Core Auth | ✅ **Completed** | Secure token rotation without reuse |
| **OIDC Discovery (`/.well-known/openid-configuration`)** | Protocols | ✅ **Completed** | RFC 8414 standard metadata discovery |
| **JWKS Keystore Endpoint (`/oauth2/jwks`)** | Cryptography | ✅ **Completed** | Public 2048-bit RSA key distribution |
| **Persistent RSA Key Rotation** | Cryptography | ✅ **Completed** | DB-persisted signing keys with zero-downtime rotation |
| **UserInfo Claims Endpoint (`/userinfo`)** | Protocols | ✅ **Completed** | Returns standard OIDC claims (`email`, `roles`, etc.) |
| **Token Revocation (`/oauth2/revoke`)** | Protocols | ✅ **Completed** | RFC 7009 token revocation support |
| **Clustered JDBC Spring Sessions** | Architecture | ✅ **Completed** | High-availability session persistence via PostgreSQL/MySQL |
| **Single-JAR Packaging** | Architecture | ✅ **Completed** | Embedded Angular 21 SPA in runnable Spring Boot JAR |
| **PKCE OAuth Client Registry** | Admin Console | ✅ **Completed** | Create & list public OAuth2 clients (`/api/admin/oauth-clients`) |
| **User Status Management** | Admin Console | ✅ **Completed** | Enable/disable user accounts with self-disable protection |
| **User Creation & Role Assignment** | Admin Console | ✅ **Completed** | `POST /api/admin/users` + Create User modal (`ROLE_ADMIN`, `ROLE_USER`) |
| **Custom Company Logo & Branding** | UI / Branding | ✅ **Completed** | Micro-badge pairing (`[Portal SSO] | [Company Logo]`), dynamic tab favicon & title |
| **Dedicated Settings Screen (`/settings`)** | Admin Console | ✅ **Completed** | Tabbed dashboard for Branding, Security Policies, OIDC Endpoints & Diagnostics |
| **Shared High-Contrast Snackbar** | Shared UI | ✅ **Completed** | Dark theme toasts with glow accents & animated auto-dismiss countdowns |
| **Product Showcase Screen (`/product`)** | UI / Marketing | ✅ **Completed** | Interactive screenshots, mockups, architecture, and specs |
| **Admin-Configured SMTP** | Notifications | ✅ **Completed** | Relay stored in DB and edited in Settings → Email (SMTP); password AES-256-GCM encrypted, test send with saved outcome |
| **Transactional Email Templates** | Notifications | ✅ **Completed** | Themed HTML + plain-text pairs for welcome, new-device alert, sign-in notice and magic links; branding, per-message switches and previews in Settings → Email Templates |
| **New Device Sign-in Alerts** | Security | ✅ **Completed** | `user_known_devices` matched on browser + OS family (not version, to avoid monthly false alarms); audited as well as emailed, and reset with an MFA reset |
| **Magic Link Sign-in & Sign-up** | Core Auth | ✅ **Completed** | Single-use SHA-256-stored tokens, 1–60 min TTL, MFA still enforced, no account enumeration, per-IP and per-address rate limits |
| **OAuth Client Edit & Deletion** | Admin Console | ⏳ **Planned** | Edit redirect URIs, scopes, or remove clients |
| **Confidential Clients (`client_secret`)** | Core Auth | ⏳ **Planned** | Hashed client secrets for backend-to-backend relying parties |
| **MFA / TOTP Authenticator** | Security | ⏳ **Planned** | RFC 6238 TOTP enrollment and 2FA login challenge |
| **Admin Audit Trail & Logs** | Governance | ⏳ **Planned** | Searchable history of client & user modifications |
| **Self-Service User Profile** | Identity | ⏳ **Planned** | User portal for password change & active sessions |
| **Go Reverse Proxy Sidecar** | Integration | ⏳ **Planned** | `portal-sso-proxy` for zero-code app protection |

---

> **Note:** rows below the SMTP entry have not been revised in some time and understate what ships
> today — MFA/TOTP, the audit trail, confidential clients, OAuth client edit/delete and the
> self-service profile are all implemented. See the README for the current feature set.

## 📈 Release Log

* **Unreleased** — Added admin-configurable outbound email: SMTP relay settings stored in the
  database and edited in Settings → Email (SMTP), with encrypted-at-rest credentials, a test send
  that works against unsaved values, and audit entries for every change. Added themed transactional
  templates (welcome, new-device alert, sign-in notice, magic links) with branding, per-message
  switches, live delivery status and previews in Settings → Email Templates; new-device sign-in
  detection; and optional passwordless magic-link sign-in and sign-up.
* **v0.1.3-alpha** — Added dedicated Settings & Configuration dashboard (`/settings`) with 4 categories (Organization Branding, Security & Token Lifecycles, OIDC Endpoints with 1-click copy, and System Runtime Diagnostics).
* **v0.1.2-alpha** — Added Custom Company Logo & Organization Branding with dynamic favicon, browser title, and refined paired micro-badge lockup.
* **v0.1.1-alpha** — Added User Creation & Role Assignment API + Interactive Admin Modal dialog (`POST /api/admin/users`) and High-Contrast Notification Snackbar.
* **v0.1.0-alpha** — Core OAuth2.1 & OIDC Server with PKCE, Persistent RSA Keystore, Spring Session JDBC, Embedded Angular 21 Console, and Product Showcase Screen.
