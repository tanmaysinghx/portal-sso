# Portal SSO: Popup Login Quickstart (Zero Boilerplate)

Portal SSO provides a seamless, Okta-style popup login window for user authentication. End users click "Sign In", authenticate inside a sleek, centered popup modal with MFA support, and the popup automatically closes—returning the user profile and tokens directly to the parent application.

---

## Why Popup Login?

| Feature | Standard Full-Page Redirect | Portal SSO Popup Login |
|---|---|---|
| **App State Preserved** | ❌ Lost unless manually serialized to session/localStorage | ✅ Intact (parent page never unloads or reloads) |
| **Callback Page Required** | ❌ Must create and host `/callback` route in your app | ✅ **Zero hosting**: Uses built-in `portal.tanmaysinghx.com/oauth2/popup-callback` |
| **External Dependencies** | ❌ Heavy libraries (`oidc-client-ts`, `angular-auth-oidc-client`) | ✅ **Zero dependencies**: 8 KB standalone SDK (`portal-sso.js`) |
| **Setup Time** | ~30-60 minutes | **Under 2 minutes** |
| **Security** | OAuth 2.1 + PKCE | **Strict OAuth 2.1 + PKCE (S256)** |

---

## 1. Quick Setup in Portal SSO Admin Console (60 Seconds)

1. Open **Portal SSO Admin Console** (`https://portal.tanmaysinghx.com`).
2. Navigate to **Applications** &rarr; Click **New application**.
3. Fill in the details:
   - **Name**: e.g., `My Customer Portal`
   - **Launch URL**: Your app URL, e.g. `http://localhost:3000` (or production domain).
   - **SSO Registration**: Choose **Create client**.
   - **Client ID**: e.g. `customer-portal-app` (public client, PKCE enabled by default).
   - **Redirect URIs**: Include:
     - `https://portal.tanmaysinghx.com/oauth2/popup-callback` *(pre-configured popup relay)*
     - `http://localhost:3000` *(or your app origin)*
4. Click **Create Application**. Done! Portal SSO displays all your credentials (`Client ID`, `Secret`, and `.env` config) in one place.

---

## 2. Install the Client Package

Choose the installation method that fits your workflow:

### Option 1: Official NPM Registry (Recommended)
```bash
npm install @tanmaysinghx/portal-sso
```

### Option 2: Direct from GitHub (Zero Registry)
```bash
npm install github:tanmaysinghx/portal-sso#path:packages/portal-sso
```

### Option 3: Direct CDN `<script>` (Zero Build Tools)
```html
<script src="https://portal.tanmaysinghx.com/portal-sso.js"></script>
```

---

## 3. Framework Integration Guides

### Option A: Plain HTML / Vanilla JavaScript

No build step required. Just load the SDK and call `loginWithPopup()`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>My App</title>
  <!-- 1. Load Portal SSO SDK -->
  <script src="https://portal.tanmaysinghx.com/portal-sso.js"></script>
</head>
<body style="font-family: sans-serif; padding: 2rem;">
  <h2>Welcome to My App</h2>

  <div id="auth-container">
    <button id="login-btn" onclick="handleLogin()">Sign in with Portal SSO</button>
  </div>

  <div id="user-profile" style="display: none;">
    <p>Signed in as: <strong id="user-email"></strong> (<span id="user-name"></span>)</p>
    <button onclick="handleLogout()">Sign Out</button>
  </div>

  <script>
    // 2. Initialize SSO Client
    const sso = new PortalSSO({
      portalUrl: 'https://portal.tanmaysinghx.com',
      clientId: 'customer-portal-app'
      // redirectUri defaults automatically to: https://portal.tanmaysinghx.com/oauth2/popup-callback
    });

    // 3. Check if already logged in on page load
    const user = sso.getCurrentUser();
    if (user) {
      renderUser(user);
    }

    async function handleLogin() {
      try {
        // 4. Opens popup login window & resolves with user & tokens
        const { user, accessToken, idToken } = await sso.loginWithPopup();
        renderUser(user);
      } catch (err) {
        alert('Login cancelled or failed: ' + err.message);
      }
    }

    function handleLogout() {
      sso.logout();
      window.location.reload();
    }

    function renderUser(user) {
      document.getElementById('auth-container').style.display = 'none';
      document.getElementById('user-profile').style.display = 'block';
      document.getElementById('user-email').textContent = user.email;
      document.getElementById('user-name').textContent = user.name;
    }
  </script>
</body>
</html>
```

---

### Option B: React / Next.js

1. Include the SDK script in your `index.html` or load via dynamic import:

```html
<!-- index.html or layout.tsx -->
<script src="https://portal.tanmaysinghx.com/portal-sso.js"></script>
```

2. Create an authentication helper (`src/auth.ts`):

```typescript
declare global {
  interface Window {
    PortalSSO: any;
  }
}

export const sso = new window.PortalSSO({
  portalUrl: 'https://portal.tanmaysinghx.com',
  clientId: 'customer-portal-app',
});
```

3. Use in any React component:

```tsx
import React, { useState, useEffect } from 'react';
import { sso } from './auth';

export function LoginButton() {
  const [user, setUser] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setUser(sso.getCurrentUser());
  }, []);

  const handleLogin = async () => {
    setLoading(true);
    try {
      const session = await sso.loginWithPopup();
      setUser(session.user);
    } catch (err: any) {
      alert(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = () => {
    sso.logout();
    setUser(null);
  };

  if (user) {
    return (
      <div className="flex items-center gap-3">
        <span>Hello, {user.name} ({user.email})</span>
        <button onClick={handleLogout} className="btn-secondary">Sign Out</button>
      </div>
    );
  }

  return (
    <button onClick={handleLogin} disabled={loading} className="btn-primary">
      {loading ? 'Signing in...' : 'Sign In with Portal SSO'}
    </button>
  );
}
```

---

### Option C: Angular (21 / Standalone Components)

1. Add the SDK to `index.html` or `angular.json`:

```html
<!-- src/index.html -->
<script src="https://portal.tanmaysinghx.com/portal-sso.js"></script>
```

2. Create an injectable Auth Service using Angular Signals (`src/app/core/auth.service.ts`):

```typescript
import { Injectable, signal } from '@angular/core';

declare const PortalSSO: any;

export interface UserProfile {
  id: string;
  email: string;
  name: string;
  roles: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private sso = new PortalSSO({
    portalUrl: 'https://portal.tanmaysinghx.com',
    clientId: 'customer-portal-app',
  });

  readonly user = signal<UserProfile | null>(this.sso.getCurrentUser());
  readonly isAuthenticated = signal<boolean>(!!this.sso.getCurrentUser());

  async login(): Promise<void> {
    try {
      const session = await this.sso.loginWithPopup();
      this.user.set(session.user);
      this.isAuthenticated.set(true);
    } catch (err: any) {
      console.error('Sign-in cancelled:', err.message);
      throw err;
    }
  }

  logout(): void {
    this.sso.logout();
    this.user.set(null);
    this.isAuthenticated.set(false);
  }

  getAccessToken(): string | null {
    const session = this.sso.getSession();
    return session ? session.accessToken : null;
  }
}
```

3. Use in your component:

```typescript
@Component({
  selector: 'app-header',
  standalone: true,
  template: `
    @if (auth.isAuthenticated()) {
      <div class="user-info">
        <span>Welcome, {{ auth.user()?.name }}</span>
        <button (click)="auth.logout()">Sign Out</button>
      </div>
    } @else {
      <button (click)="auth.login()">Sign In with Portal SSO</button>
    }
  `
})
export class HeaderComponent {
  constructor(public auth: AuthService) {}
}
```

---

### Option D: Legacy JSP / JSF / Apache Tomcat

Legacy applications can trigger the popup directly in the browser and pass the obtained JWT token to an existing Java Servlet or backend controller:

```jsp
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Enterprise Portal</title>
    <script src="https://portal.tanmaysinghx.com/portal-sso.js"></script>
</head>
<body>
    <h2>Enterprise Legacy Application</h2>

    <button onclick="popupLogin()">Sign In with Portal SSO</button>

    <script>
      var sso = new PortalSSO({
        portalUrl: 'https://portal.tanmaysinghx.com',
        clientId: 'legacy-tomcat-app'
      });

      function popupLogin() {
        sso.loginWithPopup().then(function(session) {
          // Send JWT token to Java Servlet to establish HTTP session
          var form = document.createElement('form');
          form.method = 'POST';
          form.action = '<%= request.getContextPath() %>/auth/callback';

          var tokenField = document.createElement('input');
          tokenField.type = 'hidden';
          tokenField.name = 'id_token';
          tokenField.value = session.idToken;
          form.appendChild(tokenField);

          document.body.appendChild(form);
          form.submit();
        }).catch(function(err) {
          alert('Sign in failed: ' + err.message);
        });
      }
    </script>
</body>
</html>
```

---

## 3. How the Built-in Popup Relay Works

```
User App (window)                       Portal SSO Popup Window
       │                                         │
       │─── 1. window.open(/oauth2/authorize) ──▶│
       │    (Generates PKCE verifier/state)      │
       │                                         │── 2. User signs in + MFA
       │                                         │
       │                                         │── 3. Redirects to /oauth2/popup-callback
       │                                         │
       │◀── 4. postMessage({ code, state }) ─────│
       │                                         │── 5. Popup closes automatically
       │                                                    (window.close())
       │─── 6. POST /oauth2/token ───────────────▶
       │    (Exchanges code + PKCE verifier)
       │
       │◀── 7. Receives { access_token, id_token, user }
```

1. Parent window opens `https://portal.tanmaysinghx.com/oauth2/authorize` in a centered popup with PKCE `code_challenge`.
2. User authenticates with Portal SSO (including 2FA/MFA if enabled).
3. Portal SSO redirects the popup to `https://portal.tanmaysinghx.com/oauth2/popup-callback`.
4. The callback page emits a `postMessage` containing the `code` and `state` back to the parent window and closes itself.
5. The SDK verifies the `state`, exchanges the `code` and `code_verifier` at `/oauth2/token`, parses user claims, and resolves the promise.

---

## 4. API Reference: `PortalSSO`

### Constructor
```typescript
const sso = new PortalSSO({
  portalUrl: string;     // Base URL of Portal SSO (e.g. 'https://portal.tanmaysinghx.com')
  clientId: string;      // Public OAuth 2.1 client ID
  scope?: string;        // Default: 'openid profile email'
  redirectUri?: string;  // Default: '<portalUrl>/oauth2/popup-callback'
  storageKey?: string;   // Default: 'portal_sso_session'
});
```

### Methods
- `loginWithPopup(options?: { width?: number; height?: number }): Promise<Session>`
  Opens the popup and resolves with `{ user, accessToken, idToken, expiresIn, expiresAt }`.
- `getCurrentUser(): UserProfile | null`
  Returns cached user or `null` if expired or not logged in.
- `getSession(): Session | null`
  Returns active session with access token, id token, and expiration timestamp.
- `logout(): void`
  Clears cached tokens and local session.
