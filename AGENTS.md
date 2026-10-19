# AI Agent Guidelines & Architecture Manual: Portal SSO

Welcome, AI Agent! This document provides authoritative context, architectural principles, domain knowledge, coding conventions, and operational workflows for **Portal SSO**. Read this file before proposing or making changes to ensure alignment with system constraints and security invariants.

---

## 1. Project Overview & Philosophy

**Portal SSO** is an enterprise-grade, self-hosted **OAuth 2.1 & OpenID Connect (OIDC)** Identity Provider and unified Single Sign-On admin console.

### Core Philosophy
1. **Single Deployable Unit**: A single Spring Boot JAR embeds the compiled Angular SPA directly within its static resources. An operator can download one file (`portal-sso.jar`) and run it with `java -jar portal-sso.jar`—no external database or containers required for initial boot.
2. **Zero-Friction Initial Boot (Jenkins LTS Style)**: On fresh launch without external configuration, it boots with an embedded H2 database (`~/.portal-sso/`), provisions default platform roles, bootstraps an initial admin account, and outputs an unlock key to `~/.portal-sso/secrets/initialAdminPassword`.
3. **Seamless In-App DB Migration**: The admin console includes an automated migration wizard allowing seamless zero-data-loss transfer from embedded H2 to PostgreSQL or MySQL.
4. **Security by Default**:
   - Enforces PKCE on **all** clients (public and confidential).
   - Persistent 2048-bit RSA keypairs with rotation support stored in DB.
   - Clustered sessions persisted via Spring Session JDBC.
   - Append-only administrative audit log.
   - Passwords hashed with BCrypt; TOTP MFA secrets encrypted via AES-256-GCM.
   - No default hardcoded passwords or backdoor credentials in source code.

---

## 2. Repository Structure

```
portal-sso/
├── AGENTS.md                  # This file (AI agent context & guidelines)
├── compose.yaml               # Production Docker Compose definition
├── Dockerfile                 # Multi-stage container build (Node 24 + Java 25)
├── install.sh / build.sh      # Automated Linux installer and build helpers
├── docs/                      # Architecture, deployment, download guides
│   ├── ARCHITECTURE.md
│   ├── FEATURES.md
│   ├── deployment.md
│   └── download.md
│
├── portal-server/             # Backend: Java 25 / Spring Boot 4.1.1
│   ├── pom.xml                # Maven build configuration
│   ├── src/main/java/com/tanmaysinghx/portalsso/
│   │   ├── analytics/         # Login attempt tracking, GeoIP resolution, retention jobs
│   │   ├── application/       # Portal dashboard application registry
│   │   ├── audit/             # Administrative audit trail & CSV export
│   │   ├── bootstrap/         # Initial administrator creation logic
│   │   ├── client/            # OAuth 2.1 client registry & RegisteredClientRepository
│   │   ├── common/            # BaseEntity, error handling, PageResponse, IP resolver
│   │   ├── config/            # Cross-cutting Spring configs (JPA auditing, seeding)
│   │   ├── database/          # In-app PostgreSQL/MySQL migration service & status API
│   │   ├── mail/              # Admin-configured SMTP relay, encrypted credentials, test send
│   │   ├── registration/      # Public self-registration (when enabled)
│   │   ├── security/          # Spring Security, OAuth2 Authorization Server, MFA, keys
│   │   ├── setup/             # Initial setup wizard API
│   │   └── user/              # User accounts, platform roles, credentials
│   └── src/main/resources/
│       ├── application.yml    # Main configuration
│       └── db/changelog/      # Liquibase migration change sets (YAML format)
│
└── portal-client/             # Frontend: Angular 21 SPA
    ├── package.json           # Angular & build dependencies
    ├── angular.json           # Angular workspace configuration
    └── src/app/
        ├── core/              # Auth state (Signals), guards, interceptors, services
        ├── layout/            # Shell, navigation sidebar, header
        ├── shared/            # Reusable UI components (badges, modals, charts)
        └── features/          # Feature domains:
            ├── applications/  # App catalog management
            ├── audit/         # Audit log page, CSV export, filters
            ├── auth/          # Login, registration, MFA enrollment
            ├── clients/       # OAuth client registry
            ├── dashboard/     # Sign-in analytics, KPIs, map, recency
            ├── roles/         # Role management & permissions
            ├── settings/      # Branding, 2FA, email (SMTP), DB migration, OIDC endpoints
            └── users/         # User accounts, lifecycle, unlock
```

---

## 3. Technology Stack & Dependencies

| Layer | Technologies | Key Libraries |
|---|---|---|
| **Backend** | Java 25, Spring Boot 4.1.1 | Spring Security 6, Spring Authorization Server, Spring Data JPA, Spring Session JDBC, Liquibase |
| **Frontend** | Angular 21, TypeScript, TailwindCSS v4 | Apache ECharts, Lucide Icons (SVG), RxJS |
| **Databases** | Multi-DB Support | PostgreSQL 15+, MySQL 8.0+, H2 (embedded/in-memory) |
| **Crypto** | Modern Cryptography | Bouncy Castle, BCrypt, AES-256-GCM, RSA 2048-bit |

---

## 4. Key Architectural Invariants & Rules

When modifying or adding code, you **MUST** uphold these rules:

### App Onboarding Shortcut
- For work related to adding, linking, or configuring user-facing apps and OAuth/OIDC clients, read `docs/APP_ONBOARDING.md` first. It covers the onboarding flow, where every secret lives, the admin API, and which files own what.
- New apps are created through `POST /api/admin/applications/onboard` (`ApplicationOnboardingService`), which creates the OAuth client, Application and API key in **one transaction**. Do not reintroduce multi-call creation from the frontend.
- All `.env`, redirect-URI and agent-guide text shown in the admin UI comes from `portal-client/src/app/features/applications/integration-snippets.ts`. Change it there, not in components.
- `docs/AI_AGENT_INTEGRATION.md` is the brief given to agents working in *other* repos that integrate with Portal SSO. Keep it in sync with `buildAgentGuide()`.
- Keep the distinction clear: an **Application** is a launchpad/catalog entry; an **OAuth Client** is the OIDC relying-party registration. An Application may link to an OAuth Client by `clientId`, but deleting one must not silently delete the other.
- Prefer simplifying admin workflows in the Applications UI while preserving the lower-level OAuth Clients page for advanced registry management.
- Apps can onboard themselves through "Connect with Portal SSO" (`connect/`, `docs/APP_CONNECT.md`): manifest → admin approval at `/connect/{id}` → code + verifier redeem. Never weaken the two-proof redeem or the same-origin rules.
- External app login/register APIs are protected by per-Application API keys. Keys are generated server-side, shown once, stored hashed, and must be used only from the app backend, never browser JavaScript.

### 1. Database & Portability
- **Liquibase owns all schemas**: Never rely on Hibernate `ddl-auto` in production. Always write changesets under `portal-server/src/main/resources/db/changelog/changes/`.
- **Database Agnostic**: All schemas and queries must run interchangeably on **PostgreSQL, MySQL, and H2**.
  - Store UUIDs as `VARCHAR(36)` instead of native database UUID types.
  - Avoid database-specific SQL date functions in queries (e.g., date bucketing is done in Java memory to prevent dialect bugs).
  - MySQL uses `TINYINT` for booleans; Postgres uses `BOOLEAN`.

### 2. Security & Credentials
- **No Plaintext Secrets**: Passwords must always be hashed with `PasswordEncoder`. TOTP secrets must be encrypted via `MfaService` using AES-256-GCM. The SMTP relay password is encrypted the same way, under the same key (`app.security.mfa.encryption-key`) — it is the deployment's single at-rest key, and adding a second one would be another thing for an operator to back up and lose. It is never returned by the API: `SmtpSettingsResponse` reports only `passwordSet`.
- **Never Log Secrets**: The `audit_events` and `login_events` tables must **never** record passwords, TOTP secrets, or recovery codes in `details` or `target_label`.
- **PKCE is Non-Negotiable**: PKCE (`code_challenge`) is strictly enforced on all OAuth clients, both public and confidential.
- **Protected Roles & Accounts**:
  - `ROLE_ADMIN` and `ROLE_USER` are platform roles and cannot be deleted.
  - An administrator cannot self-demote or disable their own account.
  - The last active administrator on the system can never be disabled or deleted.

### 3. Audit Trail vs. Sign-in Attempts
- **Recent Sign-in Attempts (`login_events`)**: Records every authentication attempt (IP, user agent, success/failure, GeoIP). Governed by an automated retention policy (`app.analytics.retention.login-events-hours=48` by default) with an hourly purge job.
- **Audit Log (`audit_events`)**: Append-only compliance log recording administrative changes (user created, role modified, client deleted, MFA reset, sign-in attempts, SMTP settings changed). Entries can **never** be edited or deleted through application endpoints. When auditing a configuration change, record **which fields changed, never their values** — see `SmtpSettingsService.describeChanges`.

### 4. Outbound Email
- SMTP settings are **database-stored and admin-edited** (`smtp_settings`, one row, `Settings → Email (SMTP)`), not `spring.mail.*` properties. Changing a relay must never require a restart or filesystem access on the host.
- `MailService` is the only entry point for sending; `SmtpMailSender` is the only thing that speaks SMTP. Do not construct a `JavaMailSender` elsewhere.
- Features that *need* mail should let `MailService.send` throw; features where mail is optional should check `MailService.isEnabled()` first. Never silently swallow a send failure a user is waiting on.
- STARTTLS is sent as `starttls.required`, and there is deliberately no "trust any certificate" setting. Do not add one.

### 5. Client IP Resolution
- Never inspect `X-Forwarded-For` raw headers directly in controllers. Always use `ClientIpResolver.getClientIp(request)`.
- When deployed behind reverse proxies, `server.forward-headers-strategy=FRAMEWORK` handles trusted proxy resolution centrally.

---

## 5. Development & Testing Commands

### Backend Commands (Maven)
```bash
# Build backend and package Angular SPA into JAR
./mvnw clean package -DskipTests

# Run tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=LoginEventRetentionTest

# Run multiple test classes
./mvnw test "-Dtest=LoginEventRetentionTest,AuditTrailIntegrationTest,AdminAuditControllerTest"

# Run Spring Boot server locally (dev mode)
./mvnw spring-boot:run
```

> **Windows Tip**: On Windows PowerShell, npm script execution is automatically routed through `npm.cmd` via Maven's OS-activated profile. When executing commands directly in PowerShell, use `cmd /c .\mvnw ...`.

### Frontend Commands (Angular)
```bash
cd portal-client

# Install dependencies (Node 24 pinned)
npm install

# Start local dev server (proxies /api to http://localhost:8080)
npm start

# Run unit tests
npx ng test --watch=false

# Production build
npm run build
```

---

## 6. Coding & Engineering Guidelines

### Java (Backend)
- **Records for DTOs & Config**: Use Java `record` classes for requests, responses, and `@ConfigurationProperties`.
- **Constructor Injection**: Always use constructor injection for Spring beans (avoid field `@Autowired`).
- **Transaction Boundaries**: Ensure `@Transactional` is declared explicitly where entities and lazy associations are accessed or modified.
- **Testing Rule (CRITICAL)**: **DO NOT USE `@Nested` WITH `@SpringBootTest`**. JUnit silently skips `@Nested` tests when combined with `@SpringBootTest`, resulting in a false-positive passing build. Use separate top-level test classes instead.
- **Error Codes**: All API error responses should map to `ErrorCode` constants (e.g., `PRTL-1000` through `PRTL-5000`) and raise appropriate domain exceptions (`ResourceNotFoundException`, `BusinessRuleViolationException`, etc.).

### Angular (Frontend)
- **Signals**: Leverage Angular Signals (`signal()`, `computed()`) for UI state management.
- **Standalone Components**: All components are standalone; do not create NgModules.
- **Design & Styling**: Follow TailwindCSS utility conventions. Keep typography crisp, colors curated, and components fully accessible (`aria-label`, contrast ratios, keyboard navigation).
- **RxJS Discipline**: Use `takeUntilDestroyed()` or explicit cleanup for long-lived subscriptions.

---

## 7. Common Pitfalls & Diagnostics

| Problem | Cause | Solution |
|---|---|---|
| `PSSecurityException: npm.ps1 cannot be loaded` | Windows PowerShell execution policy blocking scripts | Use `npm.cmd` or run via `cmd /c npm ...`. Maven is configured with `npm.cmd` for Windows profile. |
| `@SpringBootTest` passes with 0 tests executed | Used `@Nested` inside test class | Split into top-level test classes without `@Nested`. |
| LazyInitializationException during DTO mapping | Accessing lazy entity collections outside transaction | Add `@Transactional` to the controller/service method reading entity relations. |
| Test failures when testing custom retention properties | Properties from `application.yml` override test properties | Explicitly override properties in `@TestPropertySource(properties = {"app.analytics.retention.login-events-hours=0", ...})`. |
| Audit log missing expected actor | Calling from unauthenticated or async context | AuditService handles `anonymous` and `system` actors; verify SecurityContext propagation. |

---

*Keep this guide updated as new features, security constraints, and domain services evolve.*
