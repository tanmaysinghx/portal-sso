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
  - `GET /api/admin/users`, `PATCH /api/admin/users/{id}` — list users, enable/disable
  - `GET /api/admin/me` — current session's identity, used by the SPA to bootstrap auth state
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

- **JWK signing key regenerates on every restart.** Fine for one instance; breaks token
  verification across a restart or multiple instances. Needs to be generated once and persisted.
- **No shared session store.** The admin dashboard's login session is in-memory; won't survive
  running more than one instance without sticky sessions or an external session store (e.g. Spring
  Session JDBC, given Postgres is already a dependency).
- **OAuth clients are PKCE-only public clients.** Confidential clients (client-secret auth) aren't
  supported by the admin API yet — deliberately deferred, not an oversight.
