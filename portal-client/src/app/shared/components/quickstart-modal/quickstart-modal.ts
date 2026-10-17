import { Component, computed, input, output, signal } from '@angular/core';
import {
  agentGuideFileName,
  appRedirectUri,
  buildAgentGuide,
  downloadTextFile,
  popupRedirectUri,
} from '../../../features/applications/integration-snippets';
import { ApplicationCredentials } from '../../../features/applications/models/application.model';

export type TechStack = 'npm' | 'popup' | 'react' | 'angular' | 'spring' | 'jsp' | 'nodejs' | 'python' | 'go';

@Component({
  selector: 'app-quickstart-modal',
  standalone: true,
  templateUrl: './quickstart-modal.html',
})
export class QuickstartModal {
  readonly open = input(false);
  readonly appName = input('My Application');
  readonly appUrl = input('http://localhost:3000');
  /** Real integration settings from the server; snippets fall back to placeholders without it. */
  readonly credentials = input<ApplicationCredentials | null>(null);
  readonly dismiss = output<void>();

  readonly activeStack = signal<TechStack>('npm');
  readonly copied = signal(false);

  readonly issuerUri = computed(() => this.credentials()?.issuerUrl ?? 'http://localhost:8080');
  readonly clientId = computed(() => this.credentials()?.oidc?.clientId ?? 'your-client-id');

  /** A URI that is actually registered for the client, so copied snippets work unmodified. */
  readonly redirectUri = computed(() => {
    const creds = this.credentials();
    if (!creds) return `${this.issuerUri()}/oauth2/popup-callback`;
    return appRedirectUri(creds) ?? popupRedirectUri(creds);
  });

  copySnippet(code: string): void {
    navigator.clipboard.writeText(code).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }

  /** Same brief as the Applications page: built from real settings, contains no secrets. */
  downloadAgentPrompt(): void {
    const creds = this.credentials();
    if (!creds) return;
    downloadTextFile(agentGuideFileName(), buildAgentGuide(this.appName(), this.appUrl(), creds));
  }

  getSnippet(stack: TechStack): string {
    const issuer = this.issuerUri();
    const client = this.clientId() || 'portal-client-id';
    const redirect = this.redirectUri();

    switch (stack) {
      case 'npm':
        return `// 1. Install official NPM package:
// npm install @tanmaysinghx/portal-sso

import { PortalSSO } from '@tanmaysinghx/portal-sso';

// 2. Initialize in your React / Angular / Vue / Vite app:
export const sso = new PortalSSO({
  portalUrl: '${issuer}',
  clientId: '${client}',
  // Built-in popup callback is handled automatically without extra routes!
});

// 3. Trigger Okta-style popup login on click:
export async function login() {
  try {
    const { user, accessToken, idToken } = await sso.loginWithPopup();
    console.log('Signed in as:', user.name, user.email);
    return user;
  } catch (err) {
    console.error('Login error:', err);
  }
}

// 4. Or use the drop-in Web Component button (zero JS required):
// import '@tanmaysinghx/portal-sso';
// <portal-login-button portal-url="${issuer}" client-id="${client}"></portal-login-button>`;

      case 'popup':
        return `<!-- 1. Include Portal SSO lightweight SDK in your HTML (or bundle into React / Angular / Vue / JSP): -->
<script src="${issuer}/portal-sso.js"></script>

<!-- 2. Trigger login popup from any button: -->
<button onclick="handleLogin()">Sign in with Portal SSO</button>

<script>
  const sso = new PortalSSO({
    portalUrl: '${issuer}',
    clientId: '${client}'
    // redirectUri defaults automatically to: ${issuer}/oauth2/popup-callback
  });

  async function handleLogin() {
    try {
      // Opens popup login window (OAuth 2.1 + PKCE), resolves on success
      const { user, accessToken, idToken } = await sso.loginWithPopup();
      console.log('Logged in user:', user.email, user.name);
      alert('Welcome, ' + user.name + ' (' + user.email + ')');
    } catch (err) {
      console.error('Sign-in cancelled or failed:', err);
    }
  }

  // Check if user is already signed in on page load:
  const currentUser = sso.getCurrentUser();
  if (currentUser) {
    console.log('Already logged in:', currentUser);
  }
</script>`;

      case 'react':
        return `// 1. Install OIDC Client:
// npm install oidc-client-ts

import { UserManager } from 'oidc-client-ts';

export const userManager = new UserManager({
  authority: '${issuer}',
  client_id: '${client}',
  redirect_uri: '${redirect}',
  response_type: 'code',
  scope: 'openid profile email',
  code_challenge_method: 'S256', // RFC 7636 PKCE (enforced by Portal SSO)
});

// Trigger sign-in redirect
export async function login() {
  await userManager.signinRedirect();
}

// Handle callback route (${redirect})
export async function handleCallback() {
  const user = await userManager.signinCallback();
  console.log('Logged in user:', user.profile);
  return user;
}`;

      case 'angular':
        return `// 1. Install Angular OIDC Client:
// npm install angular-auth-oidc-client

import { provideAuth, LogLevel } from 'angular-auth-oidc-client';

export const appConfig: ApplicationConfig = {
  providers: [
    provideAuth({
      config: {
        authority: '${issuer}',
        redirectUrl: '${redirect}',
        postLogoutRedirectUri: window.location.origin,
        clientId: '${client}',
        scope: 'openid profile email',
        responseType: 'code',
        usePkce: true, // Strictly required by Portal SSO
        logLevel: LogLevel.Warn,
      },
    }),
  ],
};`;

      case 'spring':
        return `# 1. Add dependency to pom.xml:
# <dependency>
#   <groupId>org.springframework.boot</groupId>
#   <artifactId>spring-boot-starter-oauth2-client</artifactId>
# </dependency>

# 2. Add to application.yml:
spring:
  security:
    oauth2:
      client:
        registration:
          portal-sso:
            client-id: ${client}
            client-secret: \${PORTAL_CLIENT_SECRET:} # blank for public clients
            scope: openid,profile,email
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          portal-sso:
            issuer-uri: ${issuer}`;

      case 'jsp':
        return `## ZERO CODE CHANGES FOR LEGACY JSP / JSF / TOMCAT APPS!
## Place OAuth2-Proxy in front of your Tomcat or Wildfly instance.
## Run: oauth2-proxy --config=oauth2-proxy.cfg

http_address = "0.0.0.0:4180"
upstreams = [
    "http://127.0.0.1:8080/" # Your legacy Tomcat / JSP application
]

## Portal SSO OIDC Discovery
provider = "oidc"
oidc_issuer_url = "${issuer}"
client_id = "${client}"
client_secret = "\${PORTAL_CLIENT_SECRET}" # from env; omit for public clients
redirect_url = "http://localhost:4180/oauth2/callback"

## Forward user claims as HTTP headers to your JSP (request.getHeader("X-Forwarded-Email"))
pass_user_headers = true
set_xauthrequest = true
email_domains = ["*"]
cookie_secret = "random-32-char-secret-string-here!"
cookie_secure = false # Set true in HTTPS production`;

      case 'nodejs':
        return `// 1. Install openid-client:
// npm install openid-client express

const { Issuer } = require('openid-client');

async function initAuth() {
  // Auto-discover endpoints from Portal SSO
  const portalIssuer = await Issuer.discover('${issuer}');
  const client = new portalIssuer.Client({
    client_id: '${client}',
    client_secret: process.env.PORTAL_CLIENT_SECRET, // omit for public clients
    redirect_uris: ['${redirect}'],
    response_types: ['code'],
  });

  console.log('Discovered OIDC Endpoints successfully');
  return client;
}`;

      case 'python':
        return `# 1. Install Authlib:
# pip install authlib httpx

from authlib.integrations.starlette_client import OAuth

oauth = OAuth()
oauth.register(
    name='portal_sso',
    server_metadata_url='${issuer}/.well-known/openid-configuration',
    client_id='${client}',
    client_secret=os.environ.get('PORTAL_CLIENT_SECRET'),  # None for public clients
    client_kwargs={'scope': 'openid profile email'}
)`;

      case 'go':
        return `package main

import (
    "golang.org/x/oauth2"
)

var portalSSOConfig = &oauth2.Config{
    ClientID:     "${client}",
    ClientSecret: os.Getenv("PORTAL_CLIENT_SECRET"), // empty for public clients
    Scopes:       []string{"openid", "profile", "email"},
    Endpoint: oauth2.Endpoint{
        AuthURL:  "${issuer}/oauth2/authorize",
        TokenURL: "${issuer}/oauth2/token",
    },
    RedirectURL: "${redirect}",
}`;
    }
  }
}
