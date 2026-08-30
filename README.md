# Portal SSO

A self-hosted OAuth2/OIDC Identity Provider — one deployable server, one admin dashboard, built to
be dropped into other apps' login flows with minimal ceremony.

```
portal-sso/
  portal-server/   Spring Boot 4 — OAuth2/OIDC Authorization Server + admin REST API
  portal-client/   Angular 21 — admin dashboard (OAuth clients, users)
  docs/            Architecture notes and decisions
```

## Quick start

You need both running — `portal-client`'s dev server proxies API calls to `portal-server`.

```bash
# Terminal 1 — backend, http://localhost:8080
cd portal-server
./mvnw spring-boot:run -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:portalsso;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.datasource.username=sa --spring.datasource.password= \
  --app.seed.test-data=true"

# Terminal 2 — frontend, http://localhost:4200
cd portal-client
npm install
npm start
```

Open `http://localhost:4200/sign-in` and sign in with `admin@portalsso.local` / `AdminPassword123!`.

That backend command uses throwaway in-memory H2 so there's nothing to install. To keep your data,
point it at MySQL or Postgres instead:

```bash
cd portal-server
cp config/application-local.yml.example config/application-local.yml   # add your credentials
SPRING_PROFILES_ACTIVE=mysql,local ./mvnw spring-boot:run              # drop `mysql` for Postgres
```

`config/application-local.yml` is gitignored and never packaged into the built jar, so credentials
stay out of both git and your build artifacts. See [`portal-server/README.md`](portal-server/README.md)
for the full configuration model, profiles, and seeded accounts, and
[`portal-client/README.md`](portal-client/README.md) for the frontend's structure and styling.

## What's built

- Full OAuth2/OIDC authorization_code + PKCE flow, ID/access tokens carrying `email` and `roles`
  claims, OIDC discovery/JWKS/userinfo/logout endpoints.
- Admin dashboard: sign in, dashboard overview, register/list OAuth clients, list users and
  enable/disable them — all backed by a real `/api/admin/**` REST API, `ROLE_ADMIN`-gated.

## Architecture, at a glance

**One deployable, not microservices** — see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the
full reasoning. Short version: for a self-hosted product, "easy to run" and "many services" pull in
opposite directions, and the actual horizontal-scaling blockers (an ephemeral JWK signing key, no
shared session store) aren't solved by splitting services — they're solved by fixing those two
specific things. The codebase is organized by feature package (`client/`, `user/`, `security/`) so
a real split has clean seams if one is ever needed.

A Go-based SSO sidecar/reverse-proxy (for third-party apps to adopt Portal SSO with near-zero app
code) is a planned, separate future project — decoupled from `portal-server`'s own architecture.

## Status

Pre-production. See "Known limitations" in [`portal-server/README.md`](portal-server/README.md)
before deploying anywhere real.
# portal-sso
