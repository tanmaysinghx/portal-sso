<p align="center">
  <img src="assets/images/portal-logo.svg" width="96" height="96" alt="Portal SSO logo" />
</p>

<h1 align="center">Portal SSO</h1>

<p align="center">
  <strong>Self-hosted OAuth 2.1 / OpenID Connect identity provider with a built-in admin console.</strong><br />
  One jar. Embedded database to start, PostgreSQL or MySQL in production.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 4.1.1" />
  <img src="https://img.shields.io/badge/Angular-21-DD0031?logo=angular&logoColor=white" alt="Angular 21" />
  <img src="https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white" alt="Java 25" />
</p>

---

## What you get

- **OAuth 2.1 + OIDC:** authorization code + PKCE (enforced on every client, confidential ones included), refresh-token rotation, discovery, JWKS, UserInfo and revocation.
- **Application catalog:** one screen registers an app, its OAuth client and its API key, and shows every credential in one place.
- **User portal:** a launchpad of the apps each user can access, with role-restricted apps and access requests.
- **Security defaults:** BCrypt passwords, TOTP MFA (AES-256-GCM encrypted secrets), account lockout, per-IP rate limiting, an append-only audit log, and persistent rotating RSA signing keys.
- **Operations:** a single jar with the console embedded, Spring Session JDBC for clustering, and a one-click migration from the embedded H2 database to PostgreSQL or MySQL.
- **Client SDK:** [`@tanmaysinghx/portal-sso`](packages/portal-sso) provides popup or redirect login and a drop-in `<portal-login-button>`.

---

## Quick start

### Option 1 — Single jar (evaluation, small installs)

Requires Java 25+. No database or Docker needed.

```bash
curl -LO https://github.com/tanmaysinghx/portal-sso/releases/latest/download/portal-sso.jar
java -jar portal-sso.jar --httpPort=8080
```

On first start Portal SSO creates `~/.portal-sso/` (embedded H2 database, generated MFA key) and an
initial administrator, then prints the credentials:

```
Username: admin@localhost
Password: <generated>          (also in ~/.portal-sso/secrets/initialAdminPassword)
```

Sign in at `http://localhost:8080`. Other flags: `--httpListenAddress`, `--portalHome`, `--prefix`.
Linux installer and systemd unit: [docs/download.md](docs/download.md).

When you outgrow H2, go to **Settings → Database & Migration**, enter PostgreSQL or MySQL connection
details, **Test**, then **Migrate**. Schema, users, clients, signing keys and audit history are
copied, and the new connection is saved to `~/.portal-sso/portal.properties`.

### Option 2 — Docker Compose (PostgreSQL included)

```bash
git clone https://github.com/tanmaysinghx/portal-sso.git && cd portal-sso
cp .env.example .env
openssl rand -base64 24   # → DB_PASSWORD
openssl rand -base64 32   # → APP_SECURITY_MFA_ENCRYPTION_KEY
# edit .env: also set ISSUER_URL, APP_BOOTSTRAP_ADMIN_EMAIL, APP_BOOTSTRAP_ADMIN_PASSWORD
docker compose up -d
docker compose logs app | grep Bootstrapped    # confirms the admin was created
```

Compose refuses to start if any required value is missing; there are no default credentials.
To try it locally over plain `http`, also set `SERVER_SERVLET_SESSION_COOKIE_SECURE=false` in `.env`.

After the first sign-in, **remove `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD` from
`.env`**. The account already exists, and leaving the password in a file is a needless risk.

Full deployment guide (reverse proxies, TLS, managed databases): [docs/deployment.md](docs/deployment.md).

---

## Production checklist

| | Item | Why |
|---|---|---|
| ☐ | Serve over **HTTPS** and set `ISSUER_URL` to the exact public `https://` origin | It is the `iss` in every token and in discovery; clients reject mismatches. Session cookies become `Secure` automatically when it is `https://`. |
| ☐ | Use **PostgreSQL 15+ or MySQL 8+**, close to the app | H2 is for evaluation. Database latency is the largest performance factor. |
| ☐ | Set and **back up** `APP_SECURITY_MFA_ENCRYPTION_KEY` | Losing it forces every MFA user to re-enrol. |
| ☐ | Set `FORWARD_HEADERS_STRATEGY=FRAMEWORK` behind a proxy you control | Otherwise rate limits, login events and audit entries record the proxy's IP. |
| ☐ | Remove the `APP_BOOTSTRAP_ADMIN_*` values after first sign-in | They only act when no enabled admin exists. |
| ☐ | Enable MFA for administrators | **Settings → Security & Token Policies**. |
| ☐ | Leave `app.seed.test-data` **off** | It creates well-known test accounts. |
| ☐ | Back up the database | It holds users, clients, signing keys and the audit log. |

### Configuration reference

All settings are environment variables (or the equivalent Spring property).

| Variable | Default | Purpose |
|---|---|---|
| `ISSUER_URL` | `http://localhost:8080` | Public base URL; OIDC issuer |
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | embedded H2 in `~/.portal-sso` | JDBC connection (`jdbc:postgresql://…` or `jdbc:mysql://…`, dialect auto-detected) |
| `PORTAL_HOME` | `~/.portal-sso` | Data, secrets and saved config directory |
| `APP_SECURITY_MFA_ENCRYPTION_KEY` | generated into `PORTAL_HOME` | Base64 32-byte key for TOTP secrets |
| `APP_BOOTSTRAP_ADMIN_EMAIL` / `_PASSWORD` | unset (auto-generated admin on first run) | First administrator |
| `FORWARD_HEADERS_STRATEGY` | `NATIVE` | `FRAMEWORK` behind a trusted proxy, `NONE` when exposed directly |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `true` if `ISSUER_URL` is https | Override the session cookie `Secure` flag |
| `APP_REGISTRATION_ENABLED` | `false` | Allow public self-registration |
| `APP_REGISTRATION_REQUIRE_ADMIN_APPROVAL` | `false` | New self-registered accounts start disabled |

Password policy, lockout threshold, rate limits and log retention are documented inline in
[`application.yml`](portal-server/src/main/resources/application.yml).

### Outbound email (SMTP)

There is deliberately **no `SPRING_MAIL_*` configuration**. An administrator sets the relay in
**Settings → Email (SMTP)** — host, port, STARTTLS/SSL-TLS/none, optional credentials, from address
and reply-to — and the values are stored in the database, so changing a relay needs neither a
restart nor filesystem access on the host.

- **Save, then prove it.** *Send Test Email* sends one message using whatever is in the form, saved
  or not, so a relay can be verified before it is committed and switched on. The outcome is kept and
  shown on the tab, so you can see whether this server has ever actually delivered anything.
- **The password is encrypted at rest** with AES-256-GCM under `APP_SECURITY_MFA_ENCRYPTION_KEY`,
  this deployment's single at-rest key (auto-generated into `~/.portal-sso/secrets/mfa.key` when
  unset). It is never returned by the API and never written to the audit log. Back that key up and
  the SMTP password survives with your TOTP secrets; lose it and both must be re-entered.
- **STARTTLS is required, not merely offered.** A relay that stops advertising it fails rather than
  silently downgrading to plaintext. There is no "trust any certificate" option: a relay behind a
  private CA belongs in the JVM trust store.
- Changes and test sends are recorded in the audit log as *Server settings* — which fields moved,
  never their values.

Only the transport timeouts stay properties (`app.mail.connection-timeout`, `read-timeout`,
`write-timeout`, each `10s` by default), since the right value depends on the network rather than on
who is administering the console.

---

## Connect an application

1. **Applications → New application.** Enter a name and launch URL and keep **New client** selected.
   The client ID, redirect URI and popup callback are filled in for you.
2. Click **Create application**. The **Keys & setup** modal shows the client ID, any client secret
   or API key (**shown once**), the registered redirect URIs, and a ready `.env`.
3. In your app, either use the SDK…

   ```bash
   npm install @tanmaysinghx/portal-sso
   ```

   ```ts
   import { PortalSSO } from '@tanmaysinghx/portal-sso';
   const sso = new PortalSSO({ portalUrl: 'https://sso.example.com', clientId: 'my-app' });
   const { user, accessToken } = await sso.loginWithPopup();
   ```

   …or any standard OIDC library pointed at `https://sso.example.com/.well-known/openid-configuration`
   (PKCE S256 required).

Letting a coding agent do the integration? Click **Agent guide** in Keys & setup to download a
`PORTAL_SSO.md` with your app's real settings (no secrets), or hand it
[docs/AI_AGENT_INTEGRATION.md](docs/AI_AGENT_INTEGRATION.md).

Details: [docs/APP_ONBOARDING.md](docs/APP_ONBOARDING.md) · SDK reference: [docs/POPUP_LOGIN_QUICKSTART.md](docs/POPUP_LOGIN_QUICKSTART.md)

---

## Development

```bash
# Backend — http://localhost:8080
cd portal-server
./mvnw spring-boot:run

# Console with hot reload — http://localhost:4200 (proxies API and auth routes to :8080)
cd portal-client
npm install
npm start
```

For a database other than the embedded H2, copy `portal-server/config/application-local.yml.example`
to `application-local.yml` (gitignored) and run with `SPRING_PROFILES_ACTIVE=local`.

Disposable in-memory instance with seeded test data:

```bash
cd portal-server
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:portalsso;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 --app.seed.test-data=true"
```

Seeded accounts (**development only**): `admin@portalsso.local` / `AdminPassword123!`,
`testuser@portalsso.local` / `TestPassword123!`, plus the public client `test-client`
(redirect `http://127.0.0.1:8080/authorized`).

On Windows PowerShell use `cmd /c .\mvnw …` and `npm.cmd …` if script execution is blocked.

### Build & test

```bash
./build.sh                                   # console + server → portal-server/target/*.jar
cd portal-server && ./mvnw test              # backend
cd portal-client && npx ng test --watch=false  # frontend
```

CI (`.github/workflows/ci.yml`) runs in five stages:

| Stage | Jobs |
|---|---|
| 1 · Build & test | Backend suite incl. MySQL via Testcontainers, console unit tests + production build, SDK build with a check that the committed `dist/` is current |
| 2 · Static analysis | CodeQL `security-extended` for Java and TypeScript |
| 3 · Package | Docker image build and boot against PostgreSQL |
| 4 · Gate | `Stage 4 · All checks passed`, the single check to mark required in branch protection |
| 5 · Release | Tagged GitHub release with jar + SHA-256 (pushes to `main`) |

The backend job fails if fewer than 150 tests ran or any class ran zero, which guards against
`@Nested` + `@SpringBootTest` silently skipping tests. Each job writes a summary (test counts,
bundle size, image size and boot time) to the run page.

---

## Repository layout

```
portal-server/      Spring Boot 4 — authorization server, REST API, Liquibase migrations
portal-client/      Angular 21 — admin console and user portal (embedded into the jar)
packages/portal-sso Client SDK (@tanmaysinghx/portal-sso)
docs/               Architecture, deployment, onboarding and integration guides
distribution/       systemd unit
```

| Doc | For |
|---|---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Design, schema, key decisions |
| [deployment.md](docs/deployment.md) · [download.md](docs/download.md) | Running in production |
| [APP_ONBOARDING.md](docs/APP_ONBOARDING.md) | Adding apps, where secrets live, admin API |
| [AI_AGENT_INTEGRATION.md](docs/AI_AGENT_INTEGRATION.md) | Brief to give coding agents integrating an app |
| [POPUP_LOGIN_QUICKSTART.md](docs/POPUP_LOGIN_QUICKSTART.md) | SDK usage by framework |
| [AGENTS.md](AGENTS.md) | Conventions for AI agents working on this repo |
