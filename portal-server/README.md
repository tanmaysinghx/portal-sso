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
| `APP_GEOIP_DATABASE_PATH` | *(unset)* | Path to a MaxMind GeoLite2/GeoIP2 `.mmdb`. Without it, login geography reads "Unknown" and the dashboard map explains why. |
| `SPRING_PROFILES_ACTIVE` | *(none)* | e.g. `mysql,local` — see below |

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
  - `POST /api/admin/users/{id}/unlock` — clear a lockout from failed sign-ins
  - `GET /api/admin/me` — current session's identity, used by the SPA to bootstrap auth state
  - `GET /api/admin/stats?range=day|week|month|year|5y|all` — everything the dashboard renders, in
    one response
  - `GET /api/admin/stats/export?range=…` — the same window as a CSV download
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
- **No audit log for administrative actions.** Sign-in attempts are now recorded in
  `login_events`, but client registration, user creation and enable/disable/unlock still leave no
  trace.
- **`login_events` grows without bound.** There is no retention policy or rollup yet, and the
  dashboard loads one window of rows into memory to bucket them. That is the right trade at this
  size; past it the fix is a rollup table, not dialect-specific SQL.
- **Login geography needs a database you supply.** MaxMind's licence means the `.mmdb` cannot be
  bundled. Private and loopback addresses are labelled "Local network" and never resolve to a
  country, which is why the map is empty in local development.

Fixed since the first draft of this list: the JWK signing key is now persisted
(`security/key/`), and OAuth2 authorizations, consents, and admin sessions all survive a restart.
