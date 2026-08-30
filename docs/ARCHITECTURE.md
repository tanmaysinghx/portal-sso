# Architecture

## Why one deployable, not microservices

The project's main goal is easy setup, scalability, and easy integration into other apps. Those
three pull in different directions, and it's worth being explicit about how they were resolved.

**Microservices actively hurt "easy setup."** Every extra service is another thing a self-hoster
has to deploy, network together, and keep in sync. Keycloak, Authelia, and Ory Hydra all ship as a
single deployable (+ a database) for exactly this reason — a self-hosted IdP's primary competitive
feature is how little effort it takes to stand up.

**The real scaling blockers aren't monolith-shaped.** Splitting `portal-server` into services
wouldn't fix either of the two things that actually block running more than one instance today:

1. The JWK signing key is generated fresh on every boot (Spring Boot autoconfiguration default). Two
   instances would sign with different keys; a restart invalidates every outstanding token issued
   before it. Fix: generate once, persist it (DB row or mounted secret), load it on boot.
2. The admin dashboard's login session is in-memory. Two instances without sticky sessions means a
   user's session randomly disappears depending on which instance handles the next request. Fix:
   an external session store — Spring Session JDBC is the cheapest option since Postgres is already
   a hard dependency, no new infrastructure to stand up.

Fix those two things and `portal-server` scales horizontally as-is: N identical jars behind a load
balancer. That covers a lot of ground before a real service split would ever be justified.

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
    config/       Cross-cutting Spring config (JPA auditing, dev-data seeding)
  src/main/resources/db/changelog/   Liquibase migrations

portal-client/
  src/app/
    core/         Auth state (signal-based), route guards
    layout/       Shell (sidenav + header) wrapping authenticated routes
    shared/       Small reusable components (badges, etc.)
    features/     auth/ (login, forbidden), dashboard/, clients/, users/ — each with its own
                  models/services/pages, talking directly to its own /api/admin/** resource
```

## Known gaps (tracked, not accidental)

- JWK key persistence and shared session store — see above; blocks real horizontal scaling.
- OAuth clients registered via the admin API are PKCE-only public clients; confidential
  (client-secret) clients aren't supported yet.
- Audit entries are append-only through the application, but not tamper-evident against direct
  database access, and the CSV export is unsigned.
- The bootstrap administrator's password is not forced to rotate after first sign-in. There is no
  "must change password" flow anywhere in the product yet, so an operator who leaves
  `app.bootstrap.*` in place keeps a credential readable by anyone with configuration access. The
  startup log says so explicitly, but a forced rotation would be better.
