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

### Option 1: Standalone JAR (Production / Single-Process)

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

### Option 2: Local Development (Hot-Reloading)

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
