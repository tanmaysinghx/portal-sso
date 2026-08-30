import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

interface FeatureTab {
  id: string;
  name: string;
  badge: string;
  description: string;
  imageSrc: string;
  imageAlt: string;
}

interface CodeSnippet {
  id: string;
  title: string;
  language: string;
  code: string;
}

@Component({
  selector: 'app-product',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './product.html',
  styleUrl: './product.scss',
})
export class Product {
  readonly activeTab = signal<'dashboard' | 'clients' | 'users' | 'oidc'>('dashboard');
  readonly activeCodeSnippet = signal<string>('curl');
  readonly copied = signal<boolean>(false);

  readonly tabs: FeatureTab[] = [
    {
      id: 'dashboard',
      name: 'Admin Dashboard',
      badge: 'Live Overview',
      description: 'Real-time telemetry, active client counts, user statistics, and OIDC endpoint status at a glance.',
      imageSrc: 'dashboard-preview.jpg',
      imageAlt: 'Portal SSO Admin Dashboard Screenshot',
    },
    {
      id: 'clients',
      name: 'OAuth 2.1 Clients',
      badge: 'PKCE Public Clients',
      description: 'Register and manage relying party SPA and mobile applications with strict PKCE validation and customized redirect URIs.',
      imageSrc: 'clients-preview.jpg',
      imageAlt: 'Portal SSO OAuth Client Registry Screenshot',
    },
    {
      id: 'users',
      name: 'User Management',
      badge: 'Directory & RBAC',
      description: 'Manage users, assign admin roles, lock or disable accounts, and audit login activity securely.',
      imageSrc: 'users-preview.jpg',
      imageAlt: 'Portal SSO User Management Interface',
    },
    {
      id: 'oidc',
      name: 'OIDC Discovery & JWKS',
      badge: 'RFC 8414 & RFC 7517',
      description: 'Automated OpenID Connect discovery document, JSON Web Key Sets (JWKS), and userinfo claims integration.',
      imageSrc: 'clients-preview.jpg',
      imageAlt: 'Portal SSO OIDC Engine and Discovery',
    },
  ];

  readonly codeSnippets: CodeSnippet[] = [
    {
      id: 'curl',
      title: 'cURL / OIDC Discovery',
      language: 'bash',
      code: `# Fetch OpenID Connect Discovery configuration
curl -s http://localhost:8080/.well-known/openid-configuration | jq .

# Inspect the active 2048-bit RSA JWKS keys
curl -s http://localhost:8080/oauth2/jwks | jq .`,
    },
    {
      id: 'spa',
      title: 'JavaScript / PKCE Client',
      language: 'typescript',
      code: `import { UserManager } from 'oidc-client-ts';

const userManager = new UserManager({
  authority: 'http://localhost:8080',
  client_id: 'portal-web-app',
  redirect_uri: 'http://localhost:4200/callback',
  response_type: 'code',
  scope: 'openid profile email',
  code_challenge_method: 'S256', // RFC 7636 PKCE
});

// Initiates redirect to Portal SSO login
await userManager.signinRedirect();`,
    },
    {
      id: 'docker',
      title: 'Single-Process Run',
      language: 'bash',
      code: `# 1. Package single runnable JAR (Spring Boot + embedded Angular 21)
cd portal-server && ./mvnw clean package

# 2. Launch Portal SSO with MySQL or PostgreSQL
SPRING_PROFILES_ACTIVE=mysql,local java -jar target/portal-server-0.0.1-SNAPSHOT.jar

# 3. Access Admin Console at http://localhost:8080`,
    },
  ];

  readonly endpoints = [
    { name: 'OIDC Discovery', method: 'GET', path: '/.well-known/openid-configuration', desc: 'Full OpenID Connect provider configuration metadata' },
    { name: 'Authorization', method: 'GET', path: '/oauth2/authorize', desc: 'OAuth 2.1 PKCE Authorization Code endpoint' },
    { name: 'Token Exchange', method: 'POST', path: '/oauth2/token', desc: 'Issues JWT access tokens, refresh tokens & ID tokens' },
    { name: 'JWKS Keystore', method: 'GET', path: '/oauth2/jwks', desc: 'Public RSA verification keys for signature validation' },
    { name: 'UserInfo Claims', method: 'GET', path: '/userinfo', desc: 'OpenID Connect standard user profile and claims' },
    { name: 'Token Revocation', method: 'POST', path: '/oauth2/revoke', desc: 'Revokes active refresh tokens and grant chains' },
  ];

  copyCode(code: string): void {
    navigator.clipboard.writeText(code).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }

  currentSnippet(): CodeSnippet {
    return this.codeSnippets.find((s) => s.id === this.activeCodeSnippet()) ?? this.codeSnippets[0];
  }
}
