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
| **OAuth Client Edit & Deletion** | Admin Console | ⏳ **Planned** | Edit redirect URIs, scopes, or remove clients |
| **Confidential Clients (`client_secret`)** | Core Auth | ⏳ **Planned** | Hashed client secrets for backend-to-backend relying parties |
| **MFA / TOTP Authenticator** | Security | ⏳ **Planned** | RFC 6238 TOTP enrollment and 2FA login challenge |
| **Admin Audit Trail & Logs** | Governance | ⏳ **Planned** | Searchable history of client & user modifications |
| **Self-Service User Profile** | Identity | ⏳ **Planned** | User portal for password change & active sessions |
| **Go Reverse Proxy Sidecar** | Integration | ⏳ **Planned** | `portal-sso-proxy` for zero-code app protection |

---

## 📈 Release Log

* **v0.1.3-alpha** — Added dedicated Settings & Configuration dashboard (`/settings`) with 4 categories (Organization Branding, Security & Token Lifecycles, OIDC Endpoints with 1-click copy, and System Runtime Diagnostics).
* **v0.1.2-alpha** — Added Custom Company Logo & Organization Branding with dynamic favicon, browser title, and refined paired micro-badge lockup.
* **v0.1.1-alpha** — Added User Creation & Role Assignment API + Interactive Admin Modal dialog (`POST /api/admin/users`) and High-Contrast Notification Snackbar.
* **v0.1.0-alpha** — Core OAuth2.1 & OIDC Server with PKCE, Persistent RSA Keystore, Spring Session JDBC, Embedded Angular 21 Console, and Product Showcase Screen.
