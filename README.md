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

## Quick Start

### Option 1: Docker Compose (Recommended for Self-Hosting)

The fastest way to a running server. Brings up Postgres and Portal SSO together; Liquibase creates
the schema on first boot and the first administrator is created from your `.env`.

```bash
cp .env.example .env
# Fill in every value — compose refuses to start rather than invent a default.
#   openssl rand -base64 24   # DB_PASSWORD
#   openssl rand -base64 32   # APP_SECURITY_MFA_ENCRYPTION_KEY
docker compose up -d
```

Then open http://localhost:8080 and sign in with the bootstrap administrator you set. **Remove
`APP_BOOTSTRAP_ADMIN_*` from `.env` afterwards** — while they are set, anyone who can read the file
knows that account's password.

There are deliberately no default credentials in `compose.yaml`. Every secret is `${VAR:?...}`, so a
missing value stops the stack with a message naming it. A self-hosted identity server that ships
with a known admin password or a known encryption key is worse than one that will not boot.

For a local trial without TLS, set `SERVER_SERVLET_SESSION_COOKIE_SECURE=false` — session cookies
are `Secure` by default and a browser will not send them over plain http. Never do this on anything
reachable from elsewhere.

### Option 2: Standalone JAR (Production / Single-Process)

The entire Angular frontend is packaged into the Spring Boot JAR during build:

```bash
# Build the standalone runnable JAR
cd portal-server
./mvnw clean package

# Run against MySQL or Postgres
SPRING_PROFILES_ACTIVE=mysql,local ./mvnw spring-boot:run

# Or run the built JAR directly
java -jar target/portal-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql,local
```

Access the unified Admin Console directly at **`http://localhost:8080/`**.

---

### Option 3: Local Development (Hot-Reloading)

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
