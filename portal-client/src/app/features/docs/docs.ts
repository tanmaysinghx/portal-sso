import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

interface Endpoint {
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  path: string;
  summary: string;
  auth: 'Admin' | 'Session' | 'Public' | 'OAuth2';
}

interface DocSection {
  id: string;
  title: string;
}

/**
 * Reference for the endpoints this server actually exposes.
 *
 * Everything listed here was taken from the controllers and verified against a running instance —
 * nothing is aspirational. Documentation that describes endpoints which do not exist is worse than
 * no documentation, because a reader has no way to tell which half is true.
 */
@Component({
  selector: 'app-docs',
  imports: [RouterLink],
  templateUrl: './docs.html',
})
export class Docs {
  readonly copied = signal<string | null>(null);

  readonly sections: DocSection[] = [
    { id: 'quickstart', title: 'Quick start' },
    { id: 'oidc', title: 'OIDC endpoints' },
    { id: 'flow', title: 'Authorization flow' },
    { id: 'admin-api', title: 'Admin API' },
    { id: 'registration', title: 'Self-registration' },
    { id: 'config', title: 'Configuration' },
  ];

  /** Fixed by Spring Authorization Server; only the origin changes per deployment. */
  readonly oidcEndpoints: Endpoint[] = [
    { method: 'GET', path: '/.well-known/openid-configuration', summary: 'Discovery document', auth: 'Public' },
    { method: 'GET', path: '/oauth2/authorize', summary: 'Authorization endpoint (PKCE required)', auth: 'Public' },
    { method: 'POST', path: '/oauth2/token', summary: 'Exchange code or refresh token', auth: 'OAuth2' },
    { method: 'GET', path: '/oauth2/jwks', summary: 'Public signing keys', auth: 'Public' },
    { method: 'GET', path: '/userinfo', summary: 'Claims for the current access token', auth: 'OAuth2' },
    { method: 'POST', path: '/oauth2/revoke', summary: 'Revoke a token', auth: 'OAuth2' },
    { method: 'GET', path: '/connect/logout', summary: 'End the OIDC session', auth: 'Public' },
  ];

  readonly adminEndpoints: Endpoint[] = [
    { method: 'GET', path: '/api/admin/me', summary: 'Identity behind the current session', auth: 'Session' },
    { method: 'GET', path: '/api/admin/stats?range=…', summary: 'Dashboard statistics', auth: 'Admin' },
    { method: 'GET', path: '/api/admin/stats/export?range=…', summary: 'Sign-in events as CSV', auth: 'Admin' },
    { method: 'GET', path: '/api/admin/oauth-clients', summary: 'List registered applications', auth: 'Admin' },
    { method: 'POST', path: '/api/admin/oauth-clients', summary: 'Register a PKCE public client', auth: 'Admin' },
    { method: 'PUT', path: '/api/admin/oauth-clients/{id}', summary: 'Edit name, redirect URIs, scopes, enabled', auth: 'Admin' },
    { method: 'DELETE', path: '/api/admin/oauth-clients/{id}', summary: 'Delete a client and revoke its grants', auth: 'Admin' },
    { method: 'GET', path: '/api/admin/users', summary: 'List accounts with roles', auth: 'Admin' },
    { method: 'POST', path: '/api/admin/users', summary: 'Create an account', auth: 'Admin' },
    { method: 'PATCH', path: '/api/admin/users/{id}', summary: 'Enable or disable an account', auth: 'Admin' },
    { method: 'POST', path: '/api/admin/users/{id}/unlock', summary: 'Clear a failed-sign-in lockout', auth: 'Admin' },
  ];

  readonly publicEndpoints: Endpoint[] = [
    { method: 'GET', path: '/api/public/registration-policy', summary: 'Is sign-up open? Does it need approval?', auth: 'Public' },
    { method: 'POST', path: '/api/public/register', summary: 'Create an account (off by default)', auth: 'Public' },
  ];

  readonly configKeys = [
    { key: 'ISSUER_URL', value: 'http://localhost:8080', note: 'Must match how clients reach this server' },
    { key: 'DB_URL', value: 'jdbc:mysql://…/portalsso', note: 'MySQL or PostgreSQL; Liquibase owns the schema' },
    { key: 'SPRING_PROFILES_ACTIVE', value: 'mysql,local', note: 'Drop `mysql` when running on PostgreSQL' },
    { key: 'app.registration.enabled', value: 'false', note: 'Public self-registration; opt in deliberately' },
    { key: 'app.security.max-failed-login-attempts', value: '5', note: 'Consecutive failures before lockout' },
    { key: 'app.geoip.database-path', value: '(unset)', note: 'MaxMind .mmdb; without it geography reads Unknown' },
  ];

  readonly authorizeSnippet = `GET /oauth2/authorize
  ?response_type=code
  &client_id=YOUR_CLIENT_ID
  &redirect_uri=https://your-app.example.com/callback
  &scope=openid%20profile%20email
  &code_challenge=BASE64URL(SHA256(verifier))
  &code_challenge_method=S256
  &state=RANDOM`;

  readonly tokenSnippet = `curl -X POST https://sso.example.com/oauth2/token \\
  -d grant_type=authorization_code \\
  -d code=THE_CODE \\
  -d redirect_uri=https://your-app.example.com/callback \\
  -d client_id=YOUR_CLIENT_ID \\
  -d code_verifier=THE_VERIFIER`;

  readonly idTokenSnippet = `{
  "sub":   "ada@example.com",
  "email": "ada@example.com",
  "roles": ["ROLE_USER"],
  "aud":   "YOUR_CLIENT_ID",
  "iss":   "https://sso.example.com"
}`;

  methodClass(method: Endpoint['method']): string {
    // Colour is a secondary cue only — the method name is always spelled out.
    switch (method) {
      case 'GET':
        return 'bg-sky-50 text-sky-700 ring-sky-600/20';
      case 'POST':
        return 'bg-emerald-50 text-emerald-700 ring-emerald-600/20';
      case 'PUT':
      case 'PATCH':
        return 'bg-amber-50 text-amber-700 ring-amber-600/20';
      default:
        return 'bg-red-50 text-red-700 ring-red-600/20';
    }
  }

  async copy(text: string, id: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(id);
      setTimeout(() => this.copied.set(null), 1600);
    } catch {
      // Clipboard access can be denied; the snippet is still selectable by hand.
    }
  }
}
