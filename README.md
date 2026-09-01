<p align="center">
  <img src="assets/images/portal-logo.svg" width="96" height="96" alt="Portal SSO Logo" />
</p>

<h1 align="center">Portal SSO</h1>

<p align="center">
  <strong>A self-hosted OAuth2 / OIDC Identity Provider and unified Admin Console.</strong><br />
  Single deployable jar, persistent RSA signing keys, clustered JDBC sessions, and client-side SPA fallback.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 4" />
  <img src="https://img.shields.io/badge/Angular-21-DD0031?logo=angular&logoColor=white" alt="Angular 21" />
  <img src="https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white" alt="Java 25" />
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License" />
</p>

---

## Overview

Portal SSO is a self-hosted identity solution designed to be dropped into application architectures with minimal ceremony:

- **OAuth 2.1 & OpenID Connect**: Full `authorization_code` + PKCE flow, ID/Access tokens carrying `email` and `roles` claims, OIDC Discovery (`/.well-known/openid-configuration`), JWKS (`/oauth2/jwks`), UserInfo (`/userinfo`), and revocation endpoints.
- **Persistent Key Rotation**: 2048-bit RSA signing keys stored in the database with automatic rotation support; older valid keys continue verifying tokens without downtime.
- **Clustered Admin Session**: Admin dashboard sessions persisted in the database via Spring Session JDBC.
- **Embedded Admin Dashboard**: Modern Angular 21 SPA packaged directly inside the Spring Boot JAR with client-side deep linking and Remember Me support.

```
portal-sso/
├── portal-server/   # Spring Boot 4 — OAuth2/OIDC Auth Server, JDBC Session & REST API
├── portal-client/   # Angular 21 — Admin Console (OAuth clients, users, session)
├── assets/          # Brand logos and design assets
└── docs/            # Architecture notes and design decisions
```

---

## Setting up for the first time

Every command below was run end to end against a clean checkout; the output shown is what it
actually printed.

### 1. Prerequisites

Docker and Docker Compose. Nothing else — no Java, no Node, no database to install. The image
builds both halves of the project itself.

```bash
docker --version && docker compose version
```

### 2. Get the code and create your configuration

```bash
git clone https://github.com/tanmaysinghx/portal-sso.git
cd portal-sso
cp .env.example .env
```

### 3. Fill in `.env`

Every value is required. Compose refuses to start rather than invent a default, and tells you
which one is missing — a self-hosted identity server with guessable credentials is worse than one
that will not boot.

```bash
openssl rand -base64 24   # paste as DB_PASSWORD
openssl rand -base64 32   # paste as APP_SECURITY_MFA_ENCRYPTION_KEY
```

Then edit `.env`:

| Variable | Set it to |
|---|---|
| `DB_PASSWORD` | the first generated value |
| `APP_SECURITY_MFA_ENCRYPTION_KEY` | the second. **Back this up** — losing it means every user with 2FA must re-enrol |
| `ISSUER_URL` | the address browsers will actually use, e.g. `https://sso.example.com`. It is published in the OIDC discovery document and every client validates against it |
| `APP_BOOTSTRAP_ADMIN_EMAIL` | your admin address |
| `APP_BOOTSTRAP_ADMIN_PASSWORD` | 12+ characters, with upper, lower and a digit |

Trying it locally without TLS? Also uncomment `SERVER_SERVLET_SESSION_COOKIE_SECURE=false` —
session cookies are `Secure` by default and a browser will not send them over plain `http`.
Never do this on anything reachable from elsewhere.

### 4. Start it

```bash
docker compose up -d
```

First run builds the image (a few minutes). After that:

```
 Container portal-sso-db-1   Healthy
 Container portal-sso-app-1  Started
```

### 5. Check it came up

```bash
docker compose ps          # app should read (healthy)
docker compose logs app | grep Bootstrapped
```

```
WARN  c.t.p.bootstrap.AdminBootstrapper : Bootstrapped administrator 'ops@example.com' ...
```

That line means Liquibase built the schema and your administrator exists. If instead you see
*"no bootstrap credentials are configured"*, the two `APP_BOOTSTRAP_ADMIN_*` values did not reach
the container — check `.env` and re-run.

### 6. Sign in

Open `ISSUER_URL` in a browser and sign in with the bootstrap administrator.

**Then remove `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD` from `.env`.** While
they are set, anyone who can read that file knows the password. Removing them changes nothing —
the account already exists, and the bootstrap only acts when no enabled administrator is found.

### 7. Register your first application

In the console: **OAuth Clients → New client**.

- **Client ID** — what your app will identify itself as, e.g. `my-web-app`
- **Redirect URI** — where users return after signing in, e.g. `http://localhost:3000/callback`.
  It must match byte for byte what your app sends
- **Confidential client** — tick only for a server-side app that can keep a secret. Browser and
  mobile apps leave it unticked and use PKCE. A confidential client's secret is shown **once**

### 8. Point your application at it

Any standard OIDC library works; there is no SDK to install. Give it the discovery URL and it
reads everything else:

```
https://sso.example.com/.well-known/openid-configuration
```

A complete `authorization_code` + PKCE exchange against a fresh install returns:

```
token_type:   Bearer
expires_in:   899
scope:        openid profile email
id_token sub: ops@example.com
id_token iss: https://sso.example.com
roles claim:  ['ROLE_ADMIN', 'ROLE_USER']
```

**PKCE is required for every client**, confidential ones included, so your library must send a
`code_challenge`.

### 9. Before you expose it publicly

- **Put TLS in front of it** and set `ISSUER_URL` to the `https://` address.
- **Set `FORWARD_HEADERS_STRATEGY=FRAMEWORK`** if anything proxies to it (nginx, an AWS ALB,
  Cloudflare). Without it, rate limiting treats every visitor as one client and the audit log
  records the proxy's IP instead of the user's.
- **Keep the database close to the app.** The same endpoint measured 4&nbsp;ms against a local
  database and 651&nbsp;ms against one 83&nbsp;ms away. It is the largest single performance factor.
- **Back up `APP_SECURITY_MFA_ENCRYPTION_KEY`** with your database credentials.

---

## Other ways to run it

### Option A: Standalone JAR (existing database)

For an operator who already has PostgreSQL or MySQL — RDS, Aiven, Cloud SQL — and wants the
process under systemd.

```bash
cd portal-server && ./mvnw -DskipTests package

ISSUER_URL=https://sso.example.com \
DB_URL=jdbc:postgresql://db.internal:5432/portalsso \
DB_USERNAME=portal DB_PASSWORD=... \
APP_SECURITY_MFA_ENCRYPTION_KEY=... \
APP_BOOTSTRAP_ADMIN_EMAIL=ops@example.com \
APP_BOOTSTRAP_ADMIN_PASSWORD=... \
FORWARD_HEADERS_STRATEGY=FRAMEWORK \
java -jar target/portal-server-*.jar
```

For MySQL add `SPRING_PROFILES_ACTIVE=mysql`, and note that the database user needs
`SESSION_VARIABLES_ADMIN` — see [portal-server/README.md](portal-server/README.md#mysql-privileges).

### Option B: In-memory H2 (no database at all)

Data does not survive a restart. For a five-minute look, not for anything you want to keep.

```bash
cd portal-server
./mvnw spring-boot:run -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:portalsso;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.datasource.username=sa --spring.datasource.password= \
  --app.seed.test-data=true"
```

### Option C: Local development (hot-reloading)

Run the backend and frontend development servers concurrently:

```bash
# Terminal 1 — Backend (http://localhost:8080)
cd portal-server
SPRING_PROFILES_ACTIVE=mysql,local ./mvnw spring-boot:run

# Terminal 2 — Frontend Dev Server (http://localhost:4200, proxies /api & /login to :8080)
cd portal-client
npm install
npm start
```

Open **`http://localhost:4200/`** in your browser.

---

## Seeded Accounts (`app.seed.test-data=true`)

| Role | Email | Password |
|---|---|---|
| **Admin** | `admin@portalsso.local` | `AdminPassword123!` |
| **Standard User** | `testuser@portalsso.local` | `TestPassword123!` |

*Also seeds a sample PKCE public client (`test-client`, redirect URI: `http://127.0.0.1:8080/authorized`).*

---

## Continuous Integration

`.github/workflows/ci.yml` runs on every push and pull request to `main`:

| Job | What it does |
|---|---|
| **Backend tests** | JDK 25 + Node 24, `./mvnw verify`, uploads the jar and the surefire reports. Includes a Testcontainers run against real MySQL 8.0, and fails if that test was *skipped* — a skipped test proves nothing |
| **Console build** | `npm ci` + `ng build`; `npm ci` also proves the lock file is complete on Linux |
| **Docker image** | Builds the image, then **runs** it against a real Postgres and asserts it becomes healthy, serves OIDC discovery, and creates its bootstrap administrator |

The backend job additionally asserts that the suite actually executed — the count must be at least
150 and no class may report zero tests. This project has been bitten by `@Nested` with
`@SpringBootTest` silently running nothing while reporting success, and a green build that ran no
tests is worse than a red one.

Node is pinned to 24 in both CI and the Dockerfile to match the npm major that wrote
`package-lock.json`; on Node 22 (npm 10) `npm ci` fails on the platform-specific optional
dependency tree.

## Running Tests

```bash
# Run all backend integration & unit tests
cd portal-server && ./mvnw test

# Run frontend tests
cd portal-client && npx ng test --watch=false
```

---

## Configuration & Credentials

1. Copy the example configuration template:
   ```bash
   cp portal-server/config/application-local.yml.example portal-server/config/application-local.yml
   ```
2. Enter your database credentials in `portal-server/config/application-local.yml`.
3. `config/application-local.yml` is **gitignored** and never packaged into build artifacts, keeping production secrets safe.

---

## Architecture & Design Notes

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for full design rationale, database schemas, and migration details.
