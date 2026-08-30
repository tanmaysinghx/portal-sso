<p align="center">
  <img src="public/logo.svg" width="80" height="80" alt="Portal SSO Logo" />
</p>

# portal-client

The admin dashboard for **Portal SSO** — an Angular 21 (standalone components, signals) single-page
app for managing OAuth clients and users. Talks to [`portal-server`](../portal-server) over
session-cookie auth (no separate login flow of its own).

## Prerequisites

- Node.js 24+, npm
- `portal-server` running on `http://localhost:8080` (see its README) — the dev server proxies to it

## Running locally

```bash
npm install
npm start          # ng serve, http://localhost:4200
```

The dev server proxies `/api`, `/login`, and `/logout` to `localhost:8080` (`proxy.conf.json`), so
the browser only ever talks to one origin — required for the session cookie and Angular's built-in
CSRF handling to work without any CORS configuration on the backend.

Sign in with one of the seeded accounts (see `portal-server`'s README):

| Email | Password | Role |
|---|---|---|
| `admin@portalsso.local` | `AdminPassword123!` | `ROLE_ADMIN` — full dashboard access |
| `testuser@portalsso.local` | `TestPassword123!` | `ROLE_USER` — lands on `/forbidden` |

## Building

```bash
npm run build       # production build -> dist/portal-client
npm run watch        # dev build, rebuilds on change
```

## Testing

```bash
npm test             # Vitest, single run
```

## Project structure

```
src/app/
  core/               # cross-cutting: auth state, route guards
    guards/
    models/
    services/
  layout/
    shell/            # sidenav + header shell wrapping every authenticated route
  shared/
    components/       # small reusable pieces (e.g. status/scope badges)
  features/
    auth/
      login/          # /sign-in
      forbidden/      # /forbidden — authenticated but not ROLE_ADMIN
    dashboard/        # /dashboard — stat overview
    clients/          # /clients, /clients/new — OAuth client management
    users/            # /users — user list + enable/disable
  app.routes.ts
  app.config.ts
```

Each feature owns its own `models/`, `services/`, and `pages/` — there's no shared "api" layer;
each feature's service talks directly to its own REST resource under `/api/admin/**`.

## Styling: Tailwind + SCSS

- **Tailwind v4** (`src/styles.css`) does the layout, spacing, color, and typography work —
  utility classes live directly in templates. Design tokens are defined in Tailwind's CSS-based
  `@theme` block, so there is no `tailwind.config.js`.
- **SCSS** (`src/styles.scss`) covers what utilities don't: keyframe animations, the staggered
  list entrance, the custom scrollbar, and Sass mixins/variables. Component-level `.scss` files
  are used sparingly, only where a component needs more than utility composition.
- Why two style entry points (`styles.css` *and* `styles.scss`) instead of one: Tailwind's
  `@import "tailwindcss"` directive is plain CSS, not something Sass can resolve as a partial, so
  it lives in its own `.css` file loaded alongside the SCSS one (see `angular.json`'s `styles`
  array).

### Brand tokens

Both ramps are derived from the logo (`public/logo.svg`), which pairs a `#F59E0B → #EF4444`
gradient with a `#18181B` tile:

| Token | Role |
|---|---|
| `brand-500` | The logo's exact amber `#F59E0B`. Accents only — icons, active indicators, focus rings. |
| `brand-600` / `brand-700` | Deepened toward orange for filled buttons. `#F59E0B` under white text is only ~2:1 contrast, so it is never used as a text background. |
| `ink-*` | Zinc-based neutral ramp; `ink-950` matches the logo tile and is what the sidebar and sign-in screen are built on. |

Two logo files, because one asset can't serve both cases: `logo.svg` keeps the dark tile (used for
the favicon and on light surfaces), while `logo-mark.svg` drops it so the mark stays visible on the
dark sidebar.

## Auth model (why it looks the way it does)

- **Not `/login` as a route** — that path is `portal-server`'s real form-login endpoint. The
  Angular route is `/sign-in`; `AuthService.login()` POSTs to the real `/login` in the background.
- **CSRF cookie priming** — the login page's first action is a `GET /api/admin/me` call, which
  doubles as an already-logged-in check and as the way the app gets a fresh `XSRF-TOKEN` cookie
  before ever submitting the login form (Angular's `HttpClient` echoes it back automatically as
  `X-XSRF-TOKEN` on the next request).
- **Session, not JWT** — the dashboard authenticates via `portal-server`'s cookie session, the same
  mechanism the OAuth2 login page itself uses. It does not go through the OAuth2/OIDC flow it
  manages for other apps.
