<p align="center">
  <img src="../assets/images/portal-logo.svg" width="80" height="80" alt="Portal SSO Logo" />
</p>

# portal-server

The core of **Portal SSO** — a self-hosted OAuth2/OIDC Authorization Server (Spring Boot 4 +
Spring Authorization Server) with a small admin REST API bolted on for managing OAuth clients and
users. Ships as a single deployable jar; [`portal-client`](../portal-client) is its admin UI.

## Running with Docker

The repository root holds a `Dockerfile` and `compose.yaml` that package this server with the
Angular console inside a single image. See the [root README](../README.md#option-1-docker-compose-recommended-for-self-hosting).

The image builds the console and the jar in separate stages, runs as an unprivileged user, and
carries a `HEALTHCHECK` against `/actuator/health/readiness` — readiness rather than the aggregate
health group, because on first boot the server accepts connections while Liquibase is still
migrating. Tests are skipped in the image build on purpose; CI runs the full suite against a real
database instead.

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
| `APP_SECURITY_MFA_ENCRYPTION_KEY` | *(unset)* | Protects stored TOTP secrets. **Required before anyone can enrol in MFA** — see **MFA encryption key** below. |
| `APP_SECURITY_MFA_PREVIOUS_ENCRYPTION_KEY` | *(unset)* | Set only while rotating; secrets are re-encrypted at startup. |
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

### MFA encryption key

TOTP secrets are encrypted at rest with AES-256-GCM under `app.security.mfa.encryption-key`. There
is **no default**, deliberately. An earlier version fell back to a key written into the application
source — which for a self-hosted product is a *published* key, so any deployment that never set the
property stored secrets anyone could decrypt from a database dump. That is the exact failure
encrypting them was meant to prevent, and it happened silently.

Behaviour by state:

| State | What happens |
|---|---|
| Key set, all secrets readable | normal startup |
| Key set, secrets written under an older key | re-encrypted in place at startup, logged |
| Key set, secrets readable under no known key | **refuses to start**, naming the count |
| No key, no secrets stored | starts, warns; MFA enrolment returns `PRTL-1009` |
| No key, secrets stored | **refuses to start** |

Refusing is safe because the message says how to fix it and the fix is one restart. The friendlier
looking alternative — start up and skip the MFA challenge — would silently strip the second factor
from every enrolled user, which is worse than a visible outage.

**Upgrading from a build that used the default key:** set `app.security.mfa.encryption-key` to a new
value and restart. Existing secrets are detected and re-encrypted under it automatically; users keep
their existing authenticator entries. **Rotating:** set the new key, put the old one in
`app.security.mfa.previous-encryption-key`, restart, then remove the previous key.

Losing the key means every enrolled user must re-enrol (an admin can clear them with
`POST /api/admin/users/{id}/mfa/reset`), so back it up alongside your database credentials.

### MySQL privileges

The `mysql` profile issues `SET SESSION sql_require_primary_key=0` on every connection, so the
database user **must hold `SESSION_VARIABLES_ADMIN`** (or `SYSTEM_VARIABLES_ADMIN`/`SUPER`).
Without it the application fails to start with *"Access denied; you need (at least one of) the
SUPER, SYSTEM_VARIABLES_ADMIN or SESSION_VARIABLES_ADMIN privilege(s)"*. Aiven's `avnadmin` and
RDS's master user both have it; a hand-made restricted account may not. This surfaced only once the
migration path was tested against a real MySQL.

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
  - `PUT /api/admin/oauth-clients/{id}` — edit name, redirect URIs, scopes, enabled.
    `clientId` is deliberately immutable: relying apps are configured with it, so changing it
    would break every one of them with no migration path.
  - `GET /api/admin/oauth-clients?search=&enabled=&page=&size=` — paged and searchable.
  - `POST /api/admin/oauth-clients` — register a client. `confidential: true` issues a client
    secret for server-side apps (`client_secret_basic` / `client_secret_post`); omit it for a
    public PKCE-only client. **The secret is returned once, in the create response**, and stored
    only as an Argon2 hash — there is no endpoint that can show it again. PKCE is required for
    both client types, because OAuth 2.1 mandates it for every authorization_code client.
  - `DELETE /api/admin/oauth-clients/{id}` — delete the client **and revoke its grants**. The
    authorization tables reference a client by a plain column with no foreign key, so nothing
    cascades; `OAuth2GrantRevoker` removes them explicitly.
  - `GET /api/admin/users?search=&enabled=&role=&page=&size=` — paged, searchable, filterable.
    Search matches email or name; `role` uses an EXISTS subquery rather than a join, so a
    multi-role user is counted once instead of inflating the total. The page is selected without a
    fetch join and roles are loaded for that page's ids in a second query — combining a fetch join
    with `Pageable` makes Hibernate paginate **in memory** over the whole result set, which is the
    behaviour paging exists to remove.
  - `POST /api/admin/users`, `PATCH /api/admin/users/{id}` — create, enable/disable
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
- **`audit_events` has no retention.** `login_events` does (below), but the audit trail is left
  alone deliberately: it answers compliance questions, and deleting from it should be a separate
  explicit decision rather than a side effect of a convenience setting.
- **Password policy has no breach-list check.** `app.security.password` enforces length and
  composition at every entry point, but composition rules are a weak control on their own — they
  mostly produce `Password1!`. NIST 800-63B advises checking candidates against a breach corpus
  instead, which is not implemented. Raising `min-length` is worth more than enabling
  `require-symbol`.
- **Confidential clients cannot use `client_credentials`.** They get authorization_code +
  refresh_token only, so a machine-to-machine integration with no user context is not yet
  supported.
- **Registered emails are unverified.** Nothing proves a registrant owns the address they typed,
  because there is no email delivery yet. Set `app.registration.require-admin-approval: true` to
  keep a human in the loop until that lands.
- **Audit entries have no retention or export-signing story.** The trail is append-only with no
  endpoint that can edit or delete an entry, but nothing stops an operator with database access
  from rewriting it, and the CSV export is unsigned. Tamper-evidence (hash chaining, or shipping
  entries to append-only external storage) is the next step if you need it to stand up to scrutiny.
- **`login_events` retention is off by default.** Set `app.analytics.retention.login-events-days`
  to enable it (90–365 is typical); `0` keeps everything. It defaults to off because an upgrade
  that silently deleted an operator's authentication history would be a worse defect than the
  growth it fixes. Deletion runs in batches on a nightly cron so a first run against a large
  backlog does not hold one long lock.
- **The dashboard still buckets a window in memory**, now capped at 200,000 events per range. Past
  that it reports on the most recent events and logs a warning. Bucketing in Java rather than with
  SQL date functions remains deliberate — those differ across MySQL, H2 and Postgres — but at real
  scale the answer is a rollup table, not a larger cap.
- **Login geography needs a database you supply.** MaxMind's licence means the `.mmdb` cannot be
  bundled. Private and loopback addresses are labelled "Local network" and never resolve to a
  country, which is why the map is empty in local development.

Fixed since the first draft of this list: the JWK signing key is now persisted
(`security/key/`), and OAuth2 authorizations, consents, and admin sessions all survive a restart.
