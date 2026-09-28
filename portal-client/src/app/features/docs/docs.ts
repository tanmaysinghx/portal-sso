import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ThemeService } from '../../core/services/theme.service';

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

@Component({
  selector: 'app-docs',
  imports: [RouterLink],
  templateUrl: './docs.html',
})
export class Docs {
  readonly themeService = inject(ThemeService);
  readonly copied = signal<string | null>(null);

  readonly sections: DocSection[] = [
    { id: 'installation', title: '1. Installation & Setup' },
    { id: 'add-application', title: '2. Adding Applications' },
    { id: 'integration', title: '3. App Integration (OIDC)' },
    { id: 'api-reference', title: '4. API Reference' },
    { id: 'config', title: '5. Configuration' },
  ];

  readonly dockerSnippet = `# Single Container (Instant Trial)
docker run -d -p 8080:8080 -v portal-sso-data:/root/.portal-sso tanmaysinghx/portal-sso:latest`;

  readonly oidcEndpoints: Endpoint[] = [
    { method: 'GET', path: '/.well-known/openid-configuration', summary: 'Discovery document', auth: 'Public' },
    { method: 'GET', path: '/oauth2/authorize', summary: 'Authorization endpoint (PKCE required)', auth: 'Public' },
    { method: 'POST', path: '/oauth2/token', summary: 'Exchange code or refresh token', auth: 'OAuth2' },
    { method: 'GET', path: '/oauth2/jwks', summary: 'Public signing keys', auth: 'Public' },
    { method: 'GET', path: '/userinfo', summary: 'Claims for the current access token', auth: 'OAuth2' },
    { method: 'POST', path: '/oauth2/revoke', summary: 'Revoke a token', auth: 'OAuth2' },
    { method: 'GET', path: '/connect/logout', summary: 'End the OIDC session', auth: 'Public' },
  ];

  readonly configKeys = [
    { key: 'ISSUER_URL', value: 'http://localhost:8080', note: 'Must match how clients reach this server' },
    { key: 'DB_URL', value: 'jdbc:h2:...', note: 'Database URL (MySQL, PostgreSQL, H2)' },
    { key: 'app.registration.enabled', value: 'false', note: 'Allow public self-registration' },
  ];

  methodClass(method: Endpoint['method']): string {
    switch (method) {
      case 'GET': return 'bg-sky-50 text-sky-700 ring-sky-600/20';
      case 'POST': return 'bg-emerald-50 text-emerald-700 ring-emerald-600/20';
      case 'PUT':
      case 'PATCH': return 'bg-amber-50 text-amber-700 ring-amber-600/20';
      default: return 'bg-red-50 text-red-700 ring-red-600/20';
    }
  }

  async copy(text: string, id: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(id);
      setTimeout(() => this.copied.set(null), 1600);
    } catch {}
  }
}
