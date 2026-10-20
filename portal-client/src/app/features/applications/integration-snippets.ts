import { ApplicationCredentials } from './models/application.model';

/**
 * Single source for every integration artifact shown or downloaded from the Applications page:
 * the `.env` block, the redirect URI an app should use, and the AI-agent integration guide.
 * Keep all three derived from {@link ApplicationCredentials} so they can never drift apart.
 */

/** The redirect URI the SDK popup flow uses; always registered by onboarding. */
export function popupRedirectUri(creds: ApplicationCredentials): string {
  return creds.oidc?.popupCallbackUri ?? `${creds.issuerUrl}/oauth2/popup-callback`;
}

/** The app's own callback (first registered URI that isn't the portal popup relay), if any. */
export function appRedirectUri(creds: ApplicationCredentials): string | null {
  const popup = popupRedirectUri(creds);
  return creds.oidc?.redirectUris.find((uri) => uri !== popup) ?? null;
}

/**
 * `.env` for the app. Secret values appear only when they were just issued; otherwise a
 * placeholder makes it obvious the value must come from wherever the team stored it.
 */
export function buildEnvFile(appName: string, creds: ApplicationCredentials): string {
  const lines = [`# Portal SSO — ${appName}`, `PORTAL_URL=${creds.issuerUrl}`];

  if (creds.oidc) {
    lines.push(`PORTAL_CLIENT_ID=${creds.oidc.clientId}`);
    lines.push(`PORTAL_REDIRECT_URI=${appRedirectUri(creds) ?? popupRedirectUri(creds)}`);
    lines.push(`PORTAL_SCOPES=${creds.oidc.scopes.join(' ') || 'openid profile email'}`);
    if (creds.oidc.confidential) {
      lines.push('', '# Server-side only. Never ship to a browser or mobile bundle.');
      lines.push(`PORTAL_CLIENT_SECRET=${creds.oidc.clientSecret ?? '<shown once at creation — paste from your secret store>'}`);
    }
  }

  if (creds.externalAuth) {
    const ext = creds.externalAuth;
    lines.push('', '# External login/register API. Server-side only.');
    lines.push(`PORTAL_API_KEY=${ext.apiKey ?? (ext.apiKeyIssued ? `<key ${ext.apiKeyPrefix}… — paste from your secret store>` : '<not issued yet — generate one in Portal SSO>')}`);
    lines.push(`PORTAL_LOGIN_ENDPOINT=${ext.loginEndpoint}`);
    if (ext.registerEndpoint) {
      lines.push(`PORTAL_REGISTER_ENDPOINT=${ext.registerEndpoint}`);
    }
  }

  return lines.join('\n');
}

/**
 * Markdown brief for a coding agent (Claude Code, Cursor, Copilot…). Deliberately contains
 * env-var NAMES, never secret values, so it is safe to commit or paste into a chat.
 */
export function buildAgentGuide(appName: string, appUrl: string, creds: ApplicationCredentials): string {
  const oidc = creds.oidc;
  const ext = creds.externalAuth;
  const clientId = oidc?.clientId ?? '<no OAuth client linked>';
  const popup = popupRedirectUri(creds);
  const appCallback = appRedirectUri(creds);
  const scopes = oidc?.scopes.join(' ') || 'openid profile email';

  const sections: string[] = [];

  sections.push(`# Portal SSO integration brief — ${appName}

> Hand this file to a coding agent with: **"Integrate Portal SSO sign-in into this repository using PORTAL_SSO.md."**
> It contains no secrets. Secret values live in the app's \`.env\` / secret manager only.

## Facts (do not guess or change these)

| Setting | Value |
|---|---|
| Issuer / Portal URL | \`${creds.issuerUrl}\` |
| OIDC discovery | \`${creds.discoveryUrl}\` |
| Client ID | \`${clientId}\` |
| Client type | ${oidc ? (oidc.confidential ? 'Confidential (has a client secret — server-side code only)' : 'Public (PKCE only, no secret)') : 'n/a'} |
| Scopes | \`${scopes}\` |
| Registered redirect URIs | ${oidc?.redirectUris.length ? oidc.redirectUris.map((u) => `\`${u}\``).join(', ') : 'n/a'} |
| Application URL | \`${appUrl}\` |

## Hard rules

1. **PKCE (S256) is mandatory for every client**, including confidential ones. Always send \`code_challenge\` / \`code_verifier\`.
2. The \`redirect_uri\` sent to \`/oauth2/authorize\` must **exactly** match one of the registered URIs above (scheme, host, port, path, trailing slash). If you need a new one, ask a Portal SSO admin to add it — do not work around it.
3. Read configuration from environment variables (below). Never hard-code secrets, never commit \`.env\`.
${oidc?.confidential ? '4. `PORTAL_CLIENT_SECRET` is used only by server-side code (token exchange). It must never reach browser JavaScript or a mobile bundle.\n' : ''}${ext ? `${oidc?.confidential ? '5' : '4'}. \`PORTAL_API_KEY\` is used only by the app's backend. Browser code calls the app's own backend, which calls Portal SSO.\n` : ''}
## Environment variables

\`\`\`env
PORTAL_URL=${creds.issuerUrl}
PORTAL_CLIENT_ID=${clientId}
PORTAL_REDIRECT_URI=${appCallback ?? popup}
PORTAL_SCOPES=${scopes}${oidc?.confidential ? '\nPORTAL_CLIENT_SECRET=   # from secret store' : ''}${ext ? '\nPORTAL_API_KEY=         # from secret store' : ''}
\`\`\``);

  if (oidc) {
    sections.push(`## Option A — browser SPA (React, Angular, Vue, plain HTML) with the official SDK

\`\`\`bash
npm install @tanmaysinghx/portal-sso
\`\`\`

\`\`\`ts
import { PortalSSO } from '@tanmaysinghx/portal-sso';

export const sso = new PortalSSO({
  portalUrl: import.meta.env.PORTAL_URL,      // adapt to your framework's env mechanism
  clientId: import.meta.env.PORTAL_CLIENT_ID,
  // redirectUri defaults to ${popup} (already registered)
});

const session = await sso.loginWithPopup();   // { user, accessToken, idToken, expiresAt }
const accessToken = await sso.getAccessTokenAsync(); // refreshes before expiry when needed
sso.getCurrentUser();                         // cached user or null
sso.logout();
\`\`\`

Drop-in button alternative: \`import '@tanmaysinghx/portal-sso';\` then
\`<portal-login-button portal-url="..." client-id="..."></portal-login-button>\` and listen for the \`login-success\` event.

Full-page redirect instead of popup: \`sso.loginWithRedirect(PORTAL_REDIRECT_URI)\` and call \`sso.handleRedirectCallback()\` on that route. The URI must be registered.

## Option B — server-side web app (Spring, Node, Python, Go…)

Use any standards-compliant OIDC client with discovery at \`${creds.discoveryUrl}\`, authorization-code flow + PKCE S256,
\`redirect_uri = PORTAL_REDIRECT_URI\`${oidc.confidential ? ', and client authentication `client_secret_basic` (or `client_secret_post`) using `PORTAL_CLIENT_ID` / `PORTAL_CLIENT_SECRET`' : ', token endpoint auth method `none`'}.

Validate tokens with the JWKS from discovery (RS256). Do not decode JWTs without verifying the signature.`);
  }

  if (ext) {
    sections.push(`## Option C — custom login/register UI via the external API (backend only)

Header on every call: \`${ext.headerName}: $PORTAL_API_KEY\`

| Action | Endpoint | Body |
|---|---|---|
| Login | \`POST ${ext.loginEndpoint}\` | \`{ "email", "password" }\` |
${ext.registerEndpoint ? `| Register | \`POST ${ext.registerEndpoint}\` | \`{ "email", "password", "firstName", "lastName" }\` |\n` : ''}
The login call validates credentials and returns user details and whether MFA is required. It does **not** mint OAuth tokens or a Portal SSO session — use Option A/B for real SSO.`);
  }

  sections.push(`## Done when

- [ ] Config comes only from the env vars above; \`.env\` is git-ignored and \`.env.example\` lists names only.
- [ ] Sign-in round-trips successfully against \`${creds.issuerUrl}\`.
- [ ] No secret appears in client bundles, logs, or source control.
`);

  return sections.join('\n\n');
}

export function agentGuideFileName(): string {
  return 'PORTAL_SSO.md';
}

export function downloadTextFile(fileName: string, content: string, mime = 'text/markdown;charset=utf-8'): void {
  const url = URL.createObjectURL(new Blob([content], { type: mime }));
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
