# Architecture

## Why one deployable, not microservices

The project's main goal is easy setup, scalability, and easy integration into other apps. Those
three pull in different directions, and it's worth being explicit about how they were resolved.

**Microservices actively hurt "easy setup."** Every extra service is another thing a self-hoster
has to deploy, network together, and keep in sync. Keycloak, Authelia, and Ory Hydra all ship as a
single deployable (+ a database) for exactly this reason — a self-hosted IdP's primary competitive
feature is how little effort it takes to stand up.

**The real scaling blockers weren't monolith-shaped.** Splitting `portal-server` into services
would not have fixed either of the two things that actually blocked running more than one instance.
Both are now resolved:

1. The JWK signing key used to be generated fresh on every boot, so two instances would sign with
   different keys and a restart invalidated every outstanding token. It is now generated once and
   persisted (`security/key/`).
2. The admin session used to be in-memory, so without sticky sessions a user's session vanished
   depending on which instance answered. It is now in the database via Spring Session JDBC.

With those done, `portal-server` scales horizontally as-is: N identical jars behind a load balancer.
That covers a lot of ground before a real service split would be justified.

**Where a service split earns its keep:**
- A component with a genuinely different resource/scaling profile than the token-issuance hot path
  (e.g. a heavy audit-log or analytics pipeline).
- Offering the admin dashboard as hosted SaaS independent of self-hosted deployments.

Neither applies yet. The codebase is organized by feature package (`client/`, `user/`, `security/`)
specifically so that if one of these becomes real, the seams already exist.

## Where Go fits: a sidecar, not a service split

The "easy integration into other apps" goal doesn't need the core IdP touched at all — it needs a
low-friction way for a third-party app to become a relying party. The plan for that is a small Go
binary (`portal-sso-proxy`, working name) that a customer runs alongside their own app: a reverse
proxy that handles the OAuth2/OIDC client role (redirect to `portal-server`, exchange the code,
validate the session) so the protected app needs close to zero of its own auth code — the same
shape as `oauth2-proxy` or Ory Oathkeeper.

This is a separate, later project (its own repo or an isolated subfolder), independent of
`portal-server`'s internals — it only needs `portal-server` to be a spec-compliant OIDC provider,
which it already is.

## Current layout

```
portal-server/
  src/main/java/com/tanmaysinghx/portalsso/
    client/       OAuth client entity, repository, RegisteredClientRepository impl, admin API
    user/         User/Role entities, repositories, admin API
    security/     SecurityConfig, JWT claims customizer, password encoder, UserDetailsService
    audit/        Append-only administrative audit trail
    bootstrap/    First-administrator creation on a fresh deployment
    config/       Cross-cutting Spring config (JPA auditing, dev-data seeding)
  src/main/resources/db/changelog/   Liquibase migrations

portal-client/
  src/app/
    core/         Auth state (signal-based), route guards
    layout/       Shell (sidenav + header) wrapping authenticated routes
    shared/       Small reusable components (badges, etc.)
    features/     auth/, dashboard/, clients/, users/, roles/, audit/, settings/, docs/ — each with
                  its own models/services/pages, talking directly to its own /api/** resource
```

## Known gaps (tracked, not accidental)

- Confidential clients support authorization_code + refresh_token only, so a machine-to-machine
  integration needing `client_credentials` is not yet possible.
- Audit entries are append-only through the application, but not tamper-evident against direct
  database access, and the CSV export is unsigned.
- MFA key rotation re-encrypts on startup, which is fine at this scale but reads and rewrites every
  enrolled secret in one transaction. A deployment with very many enrolled users would want that
  batched.
- The bootstrap administrator's password is not forced to rotate after first sign-in. There is no
  "must change password" flow anywhere in the product yet, so an operator who leaves
  `app.bootstrap.*` in place keeps a credential readable by anyone with configuration access. The
  startup log says so explicitly, but a forced rotation would be better.
