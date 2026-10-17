# Portal SSO: AI Agent App Onboarding & Integration Playbook

> **For Users**: Share this entire file with any AI coding assistant (Cursor, Claude Code, GitHub Copilot, ChatGPT, or Antigravity) along with the prompt:  
> *"Follow the playbook in `AI_AGENT_APP_ONBOARDING_INSTRUCTIONS.md` to integrate Portal SSO authentication into this repository."*

---

## 🤖 System Prompt for AI Agents

```markdown
You are integrating Single Sign-On into this application using Portal SSO (an enterprise OAuth 2.1 & OIDC Identity Provider).
Your goal is to enable authentication with minimal boilerplate, robust security, and seamless user experience.

Key System Invariants:
1. Portal SSO strictly enforces OAuth 2.1 with PKCE (S256) on ALL clients.
2. The Portal SSO Server is hosted at: https://portal.tanmaysinghx.com (or user's PORTAL_URL).
3. The official zero-dependency client SDK is hosted at: https://portal.tanmaysinghx.com/portal-sso.js.
4. The pre-configured popup callback endpoint is: https://portal.tanmaysinghx.com/oauth2/popup-callback.
5. Never introduce heavy third-party OAuth/OIDC libraries when the official lightweight SDK or direct standard OIDC endpoints can achieve the task with zero boilerplate.
```

---

## 1. Quick App Registration Checklist

When connecting an application to Portal SSO, ensure the following configuration exists on Portal SSO (`https://portal.tanmaysinghx.com`):

### In Portal SSO Admin Console:
1. Navigate to **Applications** &rarr; **New Application**.
2. **Name**: Name of the target application (e.g. `Invoice Manager`).
3. **Launch URL**: The origin or landing page URL (e.g. `http://localhost:3000` or production URL).
4. **SSO Registration**: Choose **Create client**.
   - **Client ID**: Unique client identifier (e.g. `invoice-app-client`).
   - **Client Authentication**: `none` (Public client with PKCE for SPAs/Frontends) OR `client_secret_post` (Confidential for server-side backends).
   - **Redirect URIs**:
     - `https://portal.tanmaysinghx.com/oauth2/popup-callback` *(Required for Popup Login)*
     - Application callback URL if full-page redirect is desired (e.g. `http://localhost:3000/callback`).
   - **Scopes**: `openid profile email`.
5. Click **Create Application**.
6. **Save all issued keys in one place (`.env`)**:
   ```env
   PORTAL_URL=https://portal.tanmaysinghx.com
   PORTAL_CLIENT_ID=invoice-app-client
   PORTAL_CLIENT_SECRET=... # (only if registered as a confidential client)
   PORTAL_API_KEY=...       # (only if external login/register API was enabled)
   PORTAL_REDIRECT_URI=https://portal.tanmaysinghx.com/oauth2/popup-callback
   PORTAL_SCOPES=openid profile email
   ```

---

## 2. Package Installation

Install the client SDK in your application repository:

```bash
# Recommended: Official npm registry
npm install @tanmaysinghx/portal-sso

# Alternative: Direct from GitHub repository
npm install github:tanmaysinghx/portal-sso#path:packages/portal-sso
```

---

## 3. Frontend Integration (Pick the Matching Tech Stack)

### Stack 1: React / Next.js / Vite

#### Step 1: Add Script to HTML or Root Layout
In `index.html` (Vite / CRA) or `app/layout.tsx` (Next.js):
```html
<script src="https://portal.tanmaysinghx.com/portal-sso.js"></script>
```

#### Step 2: Create Auth Service (`src/lib/auth.ts`)
```typescript
declare global {
  interface Window {
    PortalSSO: any;
  }
}

const PORTAL_URL = process.env.NEXT_PUBLIC_PORTAL_URL || 'https://portal.tanmaysinghx.com';
const CLIENT_ID = process.env.NEXT_PUBLIC_PORTAL_CLIENT_ID || 'your-client-id';

export const sso = typeof window !== 'undefined' && window.PortalSSO
  ? new window.PortalSSO({
      portalUrl: PORTAL_URL,
      clientId: CLIENT_ID,
    })
  : null;

export async function loginWithPortal() {
  if (!sso) throw new Error('PortalSSO SDK not loaded');
  return await sso.loginWithPopup();
}

export function getCurrentUser() {
  return sso ? sso.getCurrentUser() : null;
}

export function logout() {
  if (sso) sso.logout();
}
```

#### Step 3: Implement Sign-In Button in UI
```tsx
import { useState, useEffect } from 'react';
import { loginWithPortal, getCurrentUser, logout } from '../lib/auth';

export function AuthButton() {
  const [user, setUser] = useState<any>(null);

  useEffect(() => {
    setUser(getCurrentUser());
  }, []);

  const handleLogin = async () => {
    try {
      const session = await loginWithPortal();
      setUser(session.user);
    } catch (err: any) {
      console.error('Sign-in cancelled or failed:', err.message);
    }
  };

  if (user) {
    return (
      <div className="user-profile">
        <span>Logged in as: {user.name} ({user.email})</span>
        <button onClick={() => { logout(); setUser(null); }}>Sign Out</button>
      </div>
    );
  }

  return (
    <button onClick={handleLogin} className="sso-button">
      Sign in with Portal SSO
    </button>
  );
}
```

---

### Stack 2: Angular (Standalone Components & Signals)

#### Step 1: Add Script to `src/index.html`
```html
<script src="https://portal.tanmaysinghx.com/portal-sso.js"></script>
```

#### Step 2: Create `AuthService` (`src/app/core/services/auth.service.ts`)
```typescript
import { Injectable, signal, computed } from '@angular/core';

declare const PortalSSO: any;

export interface SSOUser {
  id: string;
  email: string;
  name: string;
  roles: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly portalUrl = 'https://portal.tanmaysinghx.com';
  private readonly clientId = 'your-client-id';

  private sso = new PortalSSO({
    portalUrl: this.portalUrl,
    clientId: this.clientId,
  });

  readonly user = signal<SSOUser | null>(this.sso.getCurrentUser());
  readonly isAuthenticated = computed(() => !!this.user());

  async loginWithPopup(): Promise<SSOUser> {
    const session = await this.sso.loginWithPopup();
    this.user.set(session.user);
    return session.user;
  }

  logout(): void {
    this.sso.logout();
    this.user.set(null);
  }

  getAccessToken(): string | null {
    const session = this.sso.getSession();
    return session?.accessToken || null;
  }
}
```

#### Step 3: Add HTTP Interceptor to attach Bearer token (`src/app/core/interceptors/auth.interceptor.ts`)
```typescript
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getAccessToken();

  if (token && !req.headers.has('Authorization')) {
    const cloned = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
    return next(cloned);
  }
  return next(req);
};
```

---

### Stack 3: Legacy JSP / JSF / HTML / PHP / Tomcat

No npm build or compilation needed:

```html
<script src="https://portal.tanmaysinghx.com/portal-sso.js"></script>
<button type="button" onclick="initiateSSO()">Sign In</button>

<script>
  const sso = new PortalSSO({
    portalUrl: 'https://portal.tanmaysinghx.com',
    clientId: 'legacy-web-client'
  });

  function initiateSSO() {
    sso.loginWithPopup()
      .then(function(session) {
        // Send idToken to your server to establish a standard HTTP session
        fetch('/api/auth/session-login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ idToken: session.idToken, accessToken: session.accessToken })
        }).then(function() {
          window.location.reload();
        });
      })
      .catch(function(err) {
        alert('Authentication failed: ' + err.message);
      });
  }
</script>
```

---

## 3. Backend Integration (Validating Tokens & Securing APIs)

Any API backend serving requests from your frontend can validate JWT tokens emitted by Portal SSO using the public JWKS keys.

### OIDC Discovery Endpoints
- **Issuer URI**: `https://portal.tanmaysinghx.com`
- **JWKS URL**: `https://portal.tanmaysinghx.com/oauth2/jwks`
- **OpenID Configuration**: `https://portal.tanmaysinghx.com/.well-known/openid-configuration`

### Spring Boot 3 / 4 (`application.yml`)
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://portal.tanmaysinghx.com
```

### Node.js / Express (`jwt-verify.js`)
```javascript
const { createRemoteJWKSet, jwtVerify } = require('jose');

const JWKS = createRemoteJWKSet(new URL('https://portal.tanmaysinghx.com/oauth2/jwks'));

async function authenticateToken(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Missing bearer token' });
  }

  const token = authHeader.substring(7);
  try {
    const { payload } = await jwtVerify(token, JWKS, {
      issuer: 'https://portal.tanmaysinghx.com'
    });
    req.user = payload;
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Invalid or expired token' });
  }
}
```

### Python / FastAPI (`auth.py`)
```python
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import jwt
from jwt import PyJWKClient

JWKS_URL = "https://portal.tanmaysinghx.com/oauth2/jwks"
ISSUER = "https://portal.tanmaysinghx.com"

jwks_client = PyJWKClient(JWKS_URL)
security = HTTPBearer()

async def get_current_user(credentials: HTTPAuthorizationCredentials = Depends(security)):
    token = credentials.credentials
    try:
        signing_key = jwks_client.get_signing_key_from_jwt(token)
        data = jwt.decode(
            token,
            signing_key.key,
            algorithms=["RS256"],
            issuer=ISSUER,
            options={"verify_aud": False}
        )
        return data
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Token verification failed: {str(e)}"
        )
```

---

## 4. Headless & Programmatic Auto-Login (For AI Agents / CLI Tools)

If you are building an AI agent, automated pipeline, or custom login screen that cannot open a browser popup, use the **External Auth REST API**:

1. In Portal SSO Admin Console, edit your Application &rarr; check **Allow external app login API**.
2. Click **API Key** &rarr; **Generate Key** (`prtl_key_...`).
3. Make a direct HTTP request:

```bash
curl -X POST https://portal.tanmaysinghx.com/api/external/apps/<APPLICATION_ID>/login \
  -H "Content-Type: application/json" \
  -H "X-Portal-App-Key: prtl_key_..." \
  -d '{
    "email": "user@example.com",
    "password": "UserPassword123!"
  }'
```

**Response (200 OK)**:
```json
{
  "authenticated": true,
  "mfaRequired": false,
  "user": {
    "id": "1e5dc4b3-b827-4648-9c10-...",
    "email": "user@example.com",
    "displayName": "Jane Doe",
    "roles": ["ROLE_USER"]
  }
}
```

---

## 5. Verification & Troubleshooting Checklist

- [ ] **Popup opens and displays Portal SSO login page**: Verify `clientId` matches the registered OAuth client.
- [ ] **Popup redirects to `/oauth2/popup-callback` and immediately closes**: Verify `https://portal.tanmaysinghx.com/oauth2/popup-callback` is in the OAuth client's allowed redirect URIs.
- [ ] **Parent window receives user profile**: Verify `state` matches and PKCE token exchange succeeded.
- [ ] **Cross-Origin / CORS**: The token endpoint (`/oauth2/token`) is open for CORS requests from web clients during code exchange.
- [ ] **Token Expiry**: `sso.getCurrentUser()` automatically checks `expiresAt` and clears stale sessions.
