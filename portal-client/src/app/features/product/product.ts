import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';

interface Screen {
  id: string;
  name: string;
  description: string;
  imageSrc: string;
  imageAlt: string;
}

interface Feature {
  title: string;
  description: string;
  tag: string;
  /** SVG path data (24×24, stroke icons). */
  icon: string;
  isNew?: boolean;
}

interface Snippet {
  id: string;
  label: string;
  file: string;
  code: string;
}

/**
 * Public marketing page. Every claim here is backed by shipped behaviour — when a feature
 * changes, update the copy alongside it rather than letting the page drift into promises.
 */
@Component({
  selector: 'app-product',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './product.html',
  styleUrl: './product.scss',
})
export class Product {
  readonly version = environment.version;
  readonly mobileNavOpen = signal(false);
  readonly activeScreen = signal('dashboard');
  readonly activeSnippet = signal('sdk');
  readonly copied = signal<string | null>(null);

  readonly screens: Screen[] = [
    {
      id: 'dashboard',
      name: 'Dashboard',
      description: 'Sign-in activity, success rates, locations and recent attempts at a glance.',
      imageSrc: 'dashboard-preview.jpg',
      imageAlt: 'Portal SSO admin dashboard with sign-in analytics',
    },
    {
      id: 'clients',
      name: 'Applications & clients',
      description: 'Register an app and its OAuth client in one step; every credential lives in Keys & setup.',
      imageSrc: 'clients-preview.jpg',
      imageAlt: 'Portal SSO OAuth client registry',
    },
    {
      id: 'users',
      name: 'Users & roles',
      description: 'Directory, role assignment, account lockout and unlock, MFA resets.',
      imageSrc: 'users-preview.jpg',
      imageAlt: 'Portal SSO user management screen',
    },
    {
      id: 'signin',
      name: 'Sign-in',
      description: 'Branded sign-in with TOTP two-factor and per-app logos on the consent screen.',
      imageSrc: 'signin-preview.jpg',
      imageAlt: 'Portal SSO sign-in page',
    },
  ];

  readonly features: Feature[] = [
    {
      title: 'One-step app onboarding',
      description:
        'Name and launch URL in, OAuth client, redirect URIs and API key out, created atomically. Nothing half-made if a step fails.',
      tag: 'Applications',
      icon: 'M12 4.5v15m7.5-7.5h-15',
      isNew: true,
    },
    {
      title: 'Keys & setup in one place',
      description:
        'Client ID, registered redirect URIs, scopes, endpoints and a ready .env. Secrets are shown once, and regenerated in a click if lost.',
      tag: 'Credentials',
      icon: 'M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z',
      isNew: true,
    },
    {
      title: 'Connect with Portal SSO',
      description:
        'Apps register themselves; an admin approves on a review page. Credentials go straight to the app backend — nobody copies a secret.',
      tag: 'Zero copy-paste',
      icon: 'M13.19 8.688a4.5 4.5 0 011.242 7.244l-4.5 4.5a4.5 4.5 0 01-6.364-6.364l1.757-1.757m13.35-.622l1.757-1.757a4.5 4.5 0 00-6.364-6.364l-4.5 4.5a4.5 4.5 0 001.242 7.244',
      isNew: true,
    },
    {
      title: 'Popup login SDK',
      description:
        'npm install, two lines of config, loginWithPopup(). Or drop in <portal-login-button>. The callback relay is hosted for you.',
      tag: '@tanmaysinghx/portal-sso',
      icon: 'M17.25 6.75L22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3l-4.5 16.5',
      isNew: true,
    },
    {
      title: 'Agent-ready integration guide',
      description:
        'Download PORTAL_SSO.md with your app’s real settings and hand it to Claude Code, Cursor or Copilot. It never contains secrets.',
      tag: 'AI coding agents',
      icon: 'M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z',
      isNew: true,
    },
    {
      title: 'OAuth 2.1 & OpenID Connect',
      description:
        'Authorization code with PKCE on every client, confidential included. Discovery, JWKS, UserInfo, revocation and rotating refresh tokens.',
      tag: 'Standards',
      icon: 'M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z',
    },
    {
      title: 'App launchpad & access requests',
      description:
        'Users see the apps they can open; restricted apps show a request button that admins approve in the console.',
      tag: 'User portal',
      icon: 'M3.75 6A2.25 2.25 0 016 3.75h2.25A2.25 2.25 0 0110.5 6v2.25a2.25 2.25 0 01-2.25 2.25H6a2.25 2.25 0 01-2.25-2.25V6zM13.5 6a2.25 2.25 0 012.25-2.25H18A2.25 2.25 0 0120.25 6v2.25A2.25 2.25 0 0118 10.5h-2.25a2.25 2.25 0 01-2.25-2.25V6zM3.75 15.75A2.25 2.25 0 016 13.5h2.25a2.25 2.25 0 012.25 2.25V18a2.25 2.25 0 01-2.25 2.25H6A2.25 2.25 0 013.75 18v-2.25zM13.5 15.75a2.25 2.25 0 012.25-2.25H18a2.25 2.25 0 012.25 2.25V18A2.25 2.25 0 0118 20.25h-2.25A2.25 2.25 0 0113.5 18v-2.25z',
    },
    {
      title: 'External login & register API',
      description:
        'Keep your own sign-in form and use Portal as the account store, through a per-app key held by your backend.',
      tag: 'Custom UIs',
      icon: 'M5.25 14.25h13.5m-13.5 0a3 3 0 01-3-3m3 3a3 3 0 100 6h13.5a3 3 0 100-6m-16.5-3a3 3 0 013-3h13.5a3 3 0 013 3m-19.5 0a4.5 4.5 0 01.9-2.7L5.737 5.1a3.375 3.375 0 012.7-1.35h7.126c1.062 0 2.062.5 2.7 1.35l2.587 3.45a4.5 4.5 0 01.9 2.7m0 0a3 3 0 01-3 3m0 3h.008v.008h-.008v-.008zm0-6h.008v.008h-.008v-.008zm-3 6h.008v.008h-.008v-.008zm0-6h.008v.008h-.008v-.008z',
    },
    {
      title: 'Start on H2, move to Postgres',
      description:
        'Evaluate on the embedded database, then migrate users, clients, keys and audit history to PostgreSQL or MySQL from Settings.',
      tag: 'Migration wizard',
      icon: 'M20.25 6.375c0 2.278-3.694 4.125-8.25 4.125S3.75 8.653 3.75 6.375m16.5 0c0-2.278-3.694-4.125-8.25-4.125S3.75 4.097 3.75 6.375m16.5 0v11.25c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125V6.375m16.5 0v3.75m-16.5-3.75v3.75m16.5 0v3.75C20.25 16.153 16.556 18 12 18s-8.25-1.847-8.25-4.125v-3.75m16.5 0c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125',
    },
  ];

  readonly securityFacts = [
    { value: 'PKCE', label: 'required on every client' },
    { value: 'AES-256-GCM', label: 'encrypted TOTP secrets' },
    { value: 'RSA 2048', label: 'persistent, rotating signing keys' },
    { value: 'Append-only', label: 'administrative audit log' },
    { value: 'Hashed', label: 'client secrets and API keys' },
    { value: 'Rate limited', label: 'sign-in, token and public APIs' },
  ];

  readonly snippets: Snippet[] = [
    {
      id: 'sdk',
      label: 'SDK',
      file: 'auth.ts',
      code: `import { PortalSSO } from '@tanmaysinghx/portal-sso';

const sso = new PortalSSO({
  portalUrl: 'https://sso.example.com',
  clientId: 'my-app',
});

const { user, accessToken } = await sso.loginWithPopup();
console.log(user.email, user.roles);`,
    },
    {
      id: 'button',
      label: 'Drop-in button',
      file: 'index.html',
      code: `<script type="module">
  import '@tanmaysinghx/portal-sso';
</script>

<portal-login-button
  portal-url="https://sso.example.com"
  client-id="my-app">
</portal-login-button>`,
    },
    {
      id: 'spring',
      label: 'Any OIDC library',
      file: 'application.yml',
      code: `spring:
  security:
    oauth2:
      client:
        registration:
          portal:
            client-id: \${PORTAL_CLIENT_ID}
            client-secret: \${PORTAL_CLIENT_SECRET}
            scope: openid,profile,email
        provider:
          portal:
            issuer-uri: https://sso.example.com`,
    },
    {
      id: 'agent',
      label: 'Coding agent',
      file: 'prompt',
      code: `# Applications → your app → Keys & setup → Agent guide
# saves PORTAL_SSO.md (real settings, no secrets)

> Integrate Portal SSO sign-in into this repo
> following PORTAL_SSO.md.`,
    },
  ];

  readonly endpoints = [
    { method: 'GET', path: '/.well-known/openid-configuration', desc: 'Provider discovery metadata' },
    { method: 'GET', path: '/oauth2/authorize', desc: 'Authorization code + PKCE' },
    { method: 'POST', path: '/oauth2/token', desc: 'Access, ID and rotating refresh tokens' },
    { method: 'GET', path: '/oauth2/jwks', desc: 'Public keys for signature validation' },
    { method: 'GET', path: '/userinfo', desc: 'Standard profile claims' },
    { method: 'POST', path: '/oauth2/revoke', desc: 'Token revocation' },
  ];

  readonly runCommand = 'java -jar portal-sso.jar';

  currentScreen(): Screen {
    return this.screens.find((s) => s.id === this.activeScreen()) ?? this.screens[0];
  }

  currentSnippet(): Snippet {
    return this.snippets.find((s) => s.id === this.activeSnippet()) ?? this.snippets[0];
  }

  copy(id: string, text: string): void {
    navigator.clipboard.writeText(text).then(
      () => {
        this.copied.set(id);
        setTimeout(() => {
          if (this.copied() === id) this.copied.set(null);
        }, 2000);
      },
      () => this.copied.set(null),
    );
  }
}
