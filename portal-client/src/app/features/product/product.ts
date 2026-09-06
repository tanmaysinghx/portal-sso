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
  readonly activeTab = signal<'dashboard' | 'launchpad' | 'clients' | 'mfa' | 'migration' | 'users'>('dashboard');
  readonly activeCodeSnippet = signal<string>('curl');
  readonly activeDownloadFlavor = signal<'jar' | 'docker' | 'compose' | 'systemd'>('jar');
  readonly copied = signal<boolean>(false);
  readonly copiedChecksum = signal<boolean>(false);
  readonly copiedSnippet = signal<string | null>(null);

  readonly version = 'v25.0.8';
  readonly releaseChannel = 'LTS (Long-Term Support)';
  readonly jarDownloadUrl = '/api/public/download/portal-sso.jar';
  readonly jarChecksum = '4f8b9e2a7c6109e3bb88251e6b8c4c782729a6741b01c3e3a47da4f64722880b';
  readonly jarFileSize = '48.4 MB';

  readonly tabs: FeatureTab[] = [
    {
      id: 'dashboard',
      name: 'Admin Telemetry',
      badge: 'Live Overview',
      description: 'Real-time telemetry, active client counts, user statistics, 7-day auth activity charts, and OIDC endpoint status.',
      imageSrc: 'dashboard-preview.jpg',
      imageAlt: 'Portal SSO Admin Dashboard Screenshot',
    },
    {
      id: 'launchpad',
      name: 'Apps Launchpad',
      badge: 'Single Sign-On',
      description: 'Centralized enterprise application directory with RBAC permissions. Users launch authorized web apps in one click.',
      imageSrc: 'dashboard-preview.jpg',
      imageAlt: 'Portal SSO Applications Launchpad',
    },
    {
      id: 'clients',
      name: 'OAuth 2.1 Clients',
      badge: 'PKCE Public Clients',
      description: 'Register and manage relying party SPA and mobile applications with strict PKCE (RFC 7636) and custom redirect URIs.',
      imageSrc: 'clients-preview.jpg',
      imageAlt: 'Portal SSO OAuth Client Registry Screenshot',
    },
    {
      id: 'mfa',
      name: 'TOTP 2FA Security',
      badge: 'RFC 6238 Authenticator',
      description: 'Multi-factor authentication supporting Google Authenticator, Authy, and 1Password with 8 one-time cryptographic recovery codes.',
      imageSrc: 'dashboard-preview.jpg',
      imageAlt: 'Portal SSO TOTP 2FA Security',
    },
    {
      id: 'migration',
      name: 'Dynamic DB Migration',
      badge: 'Zero-Downtime Hot Switch',
      description: 'Start immediately on zero-config embedded H2, then migrate seamlessly to PostgreSQL or MySQL with 6-second hot reload.',
      imageSrc: 'dashboard-preview.jpg',
      imageAlt: 'Portal SSO Database Migration',
    },
    {
      id: 'users',
      name: 'User Directory & RBAC',
      badge: 'Directory & Lockouts',
      description: 'Manage users, assign admin roles, lock or disable accounts, and clear failed sign-in security lockouts.',
      imageSrc: 'users-preview.jpg',
      imageAlt: 'Portal SSO User Management Interface',
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

  readonly standaloneJarSnippet = `# 1. Download standalone runnable JAR
curl -LO http://localhost:8080/api/public/download/portal-sso.jar

# 2. Run with Java 25 (includes backend + embedded SPA console)
java -jar portal-sso.jar --httpPort=8080

# 3. Open http://localhost:8080 to complete the First-Run Setup Wizard!`;

  readonly dockerSnippet = `# 1. Run Portal SSO with persistent storage
docker run -d \\
  --name portal-sso \\
  --restart unless-stopped \\
  -p 8080:8080 \\
  -v portal-sso-data:/root/.portal-sso \\
  tanmaysinghx/portal-sso:latest

# 2. Open http://localhost:8080 to complete setup!`;

  readonly dockerComposeSnippet = `services:
  portal-sso:
    image: tanmaysinghx/portal-sso:latest
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:postgresql://db:5432/portalsso
      DB_USERNAME: portal
      DB_PASSWORD: your_db_password
    depends_on:
      db:
        condition: service_healthy
    volumes:
      - portal-data:/root/.portal-sso

  db:
    image: postgres:17-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: portalsso
      POSTGRES_USER: portal
      POSTGRES_PASSWORD: your_db_password
    volumes:
      - db-data:/var/lib/postgresql/data

volumes:
  portal-data:
  db-data:`;

  readonly systemdSnippet = `[Unit]
Description=Portal SSO Identity Provider
After=network.target

[Service]
Type=simple
User=portal
WorkingDirectory=/var/lib/portal-sso
ExecStart=/usr/bin/java -jar /opt/portal-sso/portal-sso.jar --httpPort=8080 --portalHome=/var/lib/portal-sso
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target`;

  copyCode(code: string): void {
    navigator.clipboard.writeText(code).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }

  copyChecksum(): void {
    navigator.clipboard.writeText(this.jarChecksum).then(() => {
      this.copiedChecksum.set(true);
      setTimeout(() => this.copiedChecksum.set(false), 2000);
    });
  }

  copySnippetText(text: string, id: string): void {
    navigator.clipboard.writeText(text).then(() => {
      this.copiedSnippet.set(id);
      setTimeout(() => this.copiedSnippet.set(null), 2000);
    });
  }

  currentSnippet(): CodeSnippet {
    return this.codeSnippets.find((s) => s.id === this.activeCodeSnippet()) ?? this.codeSnippets[0];
  }
}
