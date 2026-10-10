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

  readonly activeStack = signal<'react' | 'angular' | 'spring' | 'jsp' | 'nodejs' | 'python' | 'go'>('react');

  readonly snippets = {
    react: `// React / Next.js with oidc-client-ts
import { UserManager } from 'oidc-client-ts';

export const userManager = new UserManager({
  authority: 'http://localhost:8080',
  client_id: 'your-client-id',
  redirect_uri: 'http://localhost:3000/callback',
  response_type: 'code',
  scope: 'openid profile email',
  code_challenge_method: 'S256', // RFC 7636 PKCE
});

export const signIn = () => userManager.signinRedirect();`,

    angular: `// Angular with angular-auth-oidc-client
import { provideAuth, LogLevel } from 'angular-auth-oidc-client';

export const appConfig: ApplicationConfig = {
  providers: [
    provideAuth({
      config: {
        authority: 'http://localhost:8080',
        redirectUrl: 'http://localhost:4200/callback',
        clientId: 'your-client-id',
        scope: 'openid profile email',
        responseType: 'code',
        usePkce: true,
      },
    }),
  ],
};`,

    spring: `# Spring Boot application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          portal-sso:
            client-id: your-client-id
            client-secret: your-client-secret
            scope: openid,profile,email
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          portal-sso:
            issuer-uri: http://localhost:8080`,

    jsp: `## Zero-Code Reverse Proxy for Legacy JSP / JSF (oauth2-proxy.cfg)
http_address = "0.0.0.0:4180"
upstreams = ["http://127.0.0.1:8080/"] # Legacy Tomcat / Wildfly

provider = "oidc"
oidc_issuer_url = "http://localhost:8080"
client_id = "your-client-id"
client_secret = "your-client-secret"
redirect_url = "http://localhost:4180/oauth2/callback"

# Injects identity headers into JSP requests:
pass_user_headers = true
set_xauthrequest = true
email_domains = ["*"]
cookie_secret = "32-byte-secret-key-goes-here-1234"`,

    nodejs: `// Node.js / Express with openid-client
const { Issuer } = require('openid-client');

async function setupAuth() {
  const portalIssuer = await Issuer.discover('http://localhost:8080');
  const client = new portalIssuer.Client({
    client_id: 'your-client-id',
    client_secret: 'your-client-secret',
    redirect_uris: ['http://localhost:3000/callback'],
    response_types: ['code'],
  });
}`,

    python: `# Python / FastAPI / Starlette with Authlib
from authlib.integrations.starlette_client import OAuth

oauth = OAuth()
oauth.register(
    name='portal_sso',
    server_metadata_url='http://localhost:8080/.well-known/openid-configuration',
    client_id='your-client-id',
    client_secret='your-client-secret',
    client_kwargs={'scope': 'openid profile email'}
)`,

    go: `// Go with golang.org/x/oauth2
import "golang.org/x/oauth2"

var oauthConfig = &oauth2.Config{
    ClientID:     "your-client-id",
    ClientSecret: "your-client-secret",
    Scopes:       []string{"openid", "profile", "email"},
    Endpoint: oauth2.Endpoint{
        AuthURL:  "http://localhost:8080/oauth2/authorize",
        TokenURL: "http://localhost:8080/oauth2/token",
    },
    RedirectURL: "http://localhost:8080/callback",
}`
  };

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
