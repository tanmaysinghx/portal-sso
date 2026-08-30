<p align="center">
  <img src="../assets/images/portal-logo.svg" width="80" height="80" alt="Portal SSO Logo" />
</p>

# portal-server

The core of **Portal SSO** — a self-hosted OAuth2/OIDC Authorization Server (Spring Boot 4 +
Spring Authorization Server) with a small admin REST API bolted on for managing OAuth clients and
users. Ships as a single deployable jar; [`portal-client`](../portal-client) is its admin UI.

## Prerequisites

- Java 25, Maven (or use the bundled `./mvnw`)
- PostgreSQL or MySQL — or run against in-memory H2 for local dev, no database needed

## Running locally

**Against MySQL** (including managed providers like Aiven, RDS, PlanetScale):

```bash
cp config/application-local.yml.example config/application-local.yml
# edit it with your JDBC URL / username / password, then:
SPRING_PROFILES_ACTIVE=mysql,local ./mvnw spring-boot:run
```

`config/application-local.yml` is gitignored and — because Spring Boot loads `./config/` from the
working directory at *runtime* rather than from the classpath — it is never packaged into the built
jar. That's why credentials go there rather than in `src/main/resources/application.yml`. See
[Configuration](#configuration) for the two-profile split.

**Against Postgres:**

```bash
createdb portalsso   # once
./mvnw spring-boot:run
```

**Against in-memory H2** (no database to set up; data doesn't survive a restart):

```bash
./mvnw spring-boot:run -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:portalsso;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.datasource.username=sa --spring.datasource.password= \
  --app.seed.test-data=true"
```

Any of these, the app listens on `http://localhost:8080`. Liquibase creates the schema on first
boot, so point it at an **empty** database.

### Seeded accounts (`app.seed.test-data=true` only)

| Email | Password | Role |
|---|---|---|
| `admin@portalsso.local` | `AdminPassword123!` | `ROLE_ADMIN` |
| `testuser@portalsso.local` | `TestPassword123!` | `ROLE_USER` |

Plus a public, PKCE-only OAuth client (`test-client`, redirect URI
`http://127.0.0.1:8080/authorized`) for exercising the authorization_code flow end to end.

## Configuration

### Where settings live

| File | Committed? | In the jar? | Holds |
|---|---|---|---|
| `src/main/resources/application.yml` | yes | yes | defaults, env-var placeholders — **no secrets** |
| `src/main/resources/application-mysql.yml` | yes | yes | MySQL-specific behaviour — no secrets |
| `config/application-local.yml` | **no** (gitignored) | **no** | your local credentials |
| `config/application-local.yml.example` | yes | no | template to copy |

Anything under `src/main/resources` is baked into the jar, so real credentials must never go there.
`./config/` is a standard Spring Boot runtime config location that is not part of the build output —
that's the right home for them. In production, prefer the environment variables below over a file.

### Environment variables

All optional (defaults shown); these override `application.yml` and are the recommended way to
configure a real deployment:

| Variable | Default | Purpose |
|---|---|---|
| `ISSUER_URL` | `http://localhost:8080` | OIDC issuer — must match how clients reach this server |
| `DB_URL` | `jdbc:postgresql://localhost:5432/portalsso` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | DB credentials |
| `SERVER_PORT` | `8080` | HTTP port |
| `APP_BOOTSTRAP_ADMIN_EMAIL` | *(unset)* | The first administrator — see **First run** below. |
| `APP_BOOTSTRAP_ADMIN_PASSWORD` | *(unset)* | Its password. Minimum 12 characters. |
| `APP_GEOIP_DATABASE_PATH` | *(unset)* | Path to a MaxMind GeoLite2/GeoIP2 `.mmdb`. Without it, login geography reads "Unknown" and the dashboard map explains why. |
| `SPRING_PROFILES_ACTIVE` | *(none)* | e.g. `mysql,local` — see below |

### First run

A fresh deployment has no accounts, so set both bootstrap variables on the first start:

```bash
APP_BOOTSTRAP_ADMIN_EMAIL=ops@acme.com \
APP_BOOTSTRAP_ADMIN_PASSWORD='choose-something-long' \
java -jar portal-server.jar
```

Then sign in and **remove them** — while they are set, anyone who can read your configuration
knows that account's password.

Both are unset by default and nothing happens unless *both* are set, so this product never ships
with a default administrator or a password baked into it. The alternatives were rejected on those
grounds: a generated password printed at startup writes a live credential into a log stream that
is routinely shipped off-host, and an unauthenticated `/setup` page is the riskiest thing to get
subtly wrong on an identity server.

The bootstrap acts only when no **enabled** administrator exists, so leaving the variables set is
harmless on restart — and it doubles as the recovery path if every admin account ends up disabled.
If the address already has an account, it is granted `ROLE_ADMIN` and re-enabled but its password
is **never** overwritten, so editing configuration cannot be used to take over someone's account.
Start with no administrator and no variables set and the server logs a warning saying exactly
that, because otherwise the only symptom is a sign-in page that rejects every credential.

### Profiles

- **`mysql`** — activate for any MySQL deployment. Handles two MySQL-specific quirks, both
  documented inline in `application-mysql.yml`:
  1. Liquibase's `DATABASECHANGELOG` table has no primary key, which managed providers reject when
     they enforce `sql_require_primary_key` (Aiven and RDS do by default).
  2. MySQL has no native `BOOLEAN` — it's an alias for `TINYINT(1)` — so Hibernate's schema
     validation must be told to expect `TINYINT` rather than `bit`.
- **`local`** — your `config/application-local.yml` credentials.

Postgres needs neither profile; run with no `SPRING_PROFILES_ACTIVE` at all.

## What's here

- **OAuth2/OIDC Authorization Server** — `/oauth2/authorize`, `/oauth2/token`, `/.well-known/openid-configuration`,
  `/oauth2/jwks`, `/userinfo`, `/connect/logout`, etc. — almost entirely Spring Boot autoconfiguration;
  see `security/SecurityConfig.java` for the one thing it can't provide out of the box (cookie-based
  CSRF for the admin SPA, which requires taking over the security filter chains explicitly).
- **Admin REST API** (`/api/admin/**`, session-cookie auth, `ROLE_ADMIN` via `@PreAuthorize`):
  - `GET/POST /api/admin/oauth-clients` — list/register OAuth clients
  - `PUT /api/admin/oauth-clients/{id}` — edit name, redirect URIs, scopes, enabled.
    `clientId` is deliberately immutable: relying apps are configured with it, so changing it
    would break every one of them with no migration path.
  - `DELETE /api/admin/oauth-clients/{id}` — delete the client **and revoke its grants**. The
    authorization tables reference a client by a plain column with no foreign key, so nothing
    cascades; `OAuth2GrantRevoker` removes them explicitly.
  - `GET/POST /api/admin/users`, `PATCH /api/admin/users/{id}` — list, create, enable/disable
  - `PUT /api/admin/users/{id}/roles` — replace a user's roles. The complete set, not a delta.
    Refuses to remove your own administrator role, or the last **enabled** administrator — a
    disabled admin cannot sign in, so counting them as cover would strand the server.
  - `POST /api/admin/users/{id}/unlock` — clear a lockout from failed sign-ins
- **Role registry** (`/api/admin/roles`, admin only): list with per-role user counts, create, edit
  the description, delete. A role's **name is immutable** — it is the granted authority and it
  travels in the `roles` claim of every issued JWT, so renaming `ROLE_ADMIN` would sign every
  administrator out and break authorization in every relying application at once. Names must match
  `^ROLE_[A-Z0-9_]+$`, because the name becomes the authority verbatim and Spring's `hasRole('X')`
  looks for `ROLE_X`: a role called `EDITOR` would be assignable, visible and completely inert.
  `ROLE_ADMIN` and `ROLE_USER` are marked protected and cannot be deleted — `user_roles` cascades
  on delete, so removing `ROLE_ADMIN` would demote every administrator in one statement.
  - `GET /api/admin/me` — current session's identity, used by the SPA to bootstrap auth state
  - `GET /api/admin/stats?range=day|week|month|year|5y|all` — everything the dashboard renders, in
    one response
  - `GET /api/admin/stats/export?range=…` — the same window as a CSV download
  - `GET /api/admin/audit?action=&actor=&targetType=&page=&size=` — the administrative audit
    trail, newest first, in a page envelope. Paginated because it is the one table that only ever
    grows. An unrecognised `action` is a 400 rather than an ignored filter: silently widening a
    search that looks narrow is the worst outcome during an investigation.
  - `GET /api/admin/audit/actions` — the recorded action types, so the console's filter does not
    hardcode a copy of the enum
  - `GET /api/admin/audit/export?…` — the same filtered view as a CSV download
- **Public self-registration** (`/api/public/**`, the only unauthenticated API surface, still
  CSRF-protected):
  - `POST /api/public/register` — create an account. **Off by default**
    (`app.registration.enabled`). The server decides the role and enabled flag; the request body
    has no field for either, so an anonymous caller cannot mint an administrator.
  - `GET /api/public/registration-policy` — whether sign-up is open, and whether new accounts need
    approval. The sign-in page uses it to decide whether to show a "Create one" link, and relying
    applications can use it to decide whether to link users here.

  Relying applications **link** to this server's `/sign-up` page; they never create users
  themselves. A Portal SSO account grants access to every application behind this server, so no
  single application gets to decide who exists.
- **Branded sign-in and consent screens** (`templates/`) — the pages an *end user* meets during an
  OAuth2 flow, replacing Spring Security's stock form. Server-rendered rather than part of the
  console, because the browser is mid-redirect between the relying app and this server when they
  appear. When the sign-in belongs to a specific application the page shows a co-branded lockup,
  read from that client's `logoUrl`.
- **Per-client branding and consent** — `logoUrl` and `requireConsent` are now settable through the
  admin API. Consent is off per client by default, so existing clients still skip straight through.
- **Rate limiting** (`security/ratelimit/`) — per-IP token buckets on `/login`, `/api/public/**` and
  `/oauth2/token`, registered ahead of the security chain so a flood costs a map lookup rather than
  a password hash. Bucket storage is capped: without a ceiling, an attacker rotating source
  addresses would turn the limiter into a memory-exhaustion vector.
- **Login analytics.** Every sign-in attempt is written to `login_events` with its source IP, user
  agent, resolved country and the application it was for. Time buckets are computed in Java rather
  than with SQL date functions, because those differ across MySQL, H2 and Postgres — a chart that
  buckets correctly against the test database and wrongly in production is a very quiet bug.
- **Administrative audit log.** Every privileged change — user created, enabled, disabled,
  unlocked, MFA reset; OAuth client registered, updated, deleted; self-registration — is written to
  `audit_events` with the actor, the target, the source IP and a summary of what changed. Two
  choices are deliberate and opposite to the analytics recorder: the write **joins the caller's
  transaction**, so the entry and the change commit together or not at all (an entry describing a
  change that rolled back would be worse than no entry); and it **does not swallow failures**,
  because a privileged change with no record of it is worse than one that failed and can be
  retried. Credentials never reach it — a test asserts no submitted password appears in any field.
- **Login tracking and lockout** — `LoginAttemptListener` stamps `last_login_at` on success and
  locks an account after `app.security.max-failed-login-attempts` consecutive failures (default 5).
  A successful sign-in resets the counter. Because it listens to authentication *events* rather
  than hooking the form-login filter, it covers the OAuth2 flows and remember-me too.
- **ID tokens / access tokens** carry `email` and `roles` claims (scope-gated per OIDC convention),
  not just `sub` — see `security/JwtClaimsCustomizerConfig.java`.

## Testing

```bash
./mvnw test
```

Includes a schema/entity-mapping validation test (`SchemaValidationTest`, catches Liquibase ↔
Hibernate drift against H2) and a full MockMvc-driven authorization_code + PKCE flow integration
test (`JwtClaimsCustomizerIntegrationTest`).

## Known limitations (not yet production-hardened)

- **Tests only exercise the non-MySQL migration path.** `007-create-spring-session-tables.yaml`
  ships two variants of `SPRING_SESSION_ATTRIBUTES` — `BLOB` for MySQL, `BYTEA` for everything
  else — selected by Liquibase's `dbms` attribute. The suite runs on H2, so it validates the
  `!mysql` branch while production runs the `mysql` one. Both have been verified by hand against a
  real MySQL instance, but nothing stops that branch regressing silently. Testcontainers would
  close this gap.
- **OAuth clients are PKCE-only public clients.** Confidential clients (client-secret auth) aren't
  supported by the admin API yet — deliberately deferred, not an oversight.
- **MFA is schema-only.** `mfa_enabled` and `mfa_secret` exist on the user table but nothing reads
  or writes them — no enrolment, challenge, or recovery codes.
- **Rate limits are per instance.** Buckets live in the JVM, so behind a load balancer the
  effective ceiling is the configured rate times the instance count. That is a deliberate trade
  against putting a shared store on the hot path of every sign-in; the per-account lockout is the
  hard stop, and a reverse proxy sees all traffic if you need a global limit.
- **Registered emails are unverified.** Nothing proves a registrant owns the address they typed,
  because there is no email delivery yet. Set `app.registration.require-admin-approval: true` to
  keep a human in the loop until that lands.
- **Audit entries have no retention or export-signing story.** The trail is append-only with no
  endpoint that can edit or delete an entry, but nothing stops an operator with database access
  from rewriting it, and the CSV export is unsigned. Tamper-evidence (hash chaining, or shipping
  entries to append-only external storage) is the next step if you need it to stand up to scrutiny.
- **`login_events` and `audit_events` grow without bound.** There is no retention policy or rollup yet, and the
  dashboard loads one window of rows into memory to bucket them. That is the right trade at this
  size; past it the fix is a rollup table, not dialect-specific SQL.
- **Login geography needs a database you supply.** MaxMind's licence means the `.mmdb` cannot be
  bundled. Private and loopback addresses are labelled "Local network" and never resolve to a
  country, which is why the map is empty in local development.

Fixed since the first draft of this list: the JWK signing key is now persisted
(`security/key/`), and OAuth2 authorizations, consents, and admin sessions all survive a restart.
