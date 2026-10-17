# Portal SSO: AI Agent & Application Integration Guide

This guide is designed for **developers and autonomous AI agents** building applications that use **Portal SSO** for authentication, user registration, and programmatic auto-login.

---

## 1. Architecture & Mental Model

Portal SSO provides two distinct integration paths depending on your application needs:

```
┌────────────────────────────────────────────────────────────────────────┐
│                              Portal SSO                                │
│                                                                        │
│  ┌──────────────────────────────┐    ┌──────────────────────────────┐  │
│  │   External Auth API (REST)   │    │  OAuth 2.1 / OIDC Provider   │  │
│  │                              │    │                              │  │
│  │  • Programmatic Auto-Login   │    │  • Interactive Browser SSO   │  │
│  │  • Machine / Agent Auth      │    │  • Auth Code + PKCE (S256)   │  │
│  │  • Headless User Reg.        │    │  • Centralized Token Grants  │  │
│  │  • Direct JSON Verification  │    │  • Standard JWT ID Tokens    │  │
│  └──────────────▲───────────────┘    └──────────────▲───────────────┘  │
└─────────────────┼───────────────────────────────────┼──────────────────┘
                  │ X-Portal-App-Key                  │ OIDC Redirect / PKCE
       ┌──────────┴──────────┐             ┌──────────┴──────────┐
       │   AI Agent / CLI    │             │  Web Frontend / SPA │
       │   Backend Service   │             │   End-User Client   │
       └─────────────────────┘             └─────────────────────┘
```

| Feature | External Auth API (Recommended for AI Agents) | OAuth 2.1 & OpenID Connect |
|---|---|---|
| **Best For** | AI Agents, automated scripts, CLI tools, custom login UIs | Standard web applications, user-facing SPAs |
| **Authentication Flow** | Direct backend REST calls with JSON payloads | Browser redirection with Authorization Code + PKCE |
| **Credentials Required** | `Application ID` + `X-Portal-App-Key` | `Client ID` (+ `Client Secret` if confidential) |
| **Session Model** | Stateless (returns user profile; app manages session) | Stateful OIDC tokens (Access Token, Refresh Token, ID Token) |
| **CSRF / Cookies** | **Exempt** (Stateless REST API) | Enforced on web endpoints |

---

## 2. Portal SSO Admin Setup (One-Time)

Before an AI agent or application can authenticate, configure the application in the Portal SSO Admin Console:

1. Navigate to **Applications** (`/applications`) &rarr; Click **New application**.
2. Fill in:
   - **Name**: Your app name (e.g., `AI Assistant Platform`).
   - **Launch URL**: Your application's URL (e.g., `http://localhost:3000` or production URL).
   - **Category**: (e.g., `Productivity`, `AI Tools`).
3. Under **External Authentication**:
   - Toggle **Allow external app login API** &rarr; `Enabled`.
   - Toggle **Allow external app registration API** &rarr; `Enabled`.
4. Click **Create Application**.
5. In the Applications table, locate your app and click **API Key** &rarr; **Generate Key**.
6. **Save the generated API key immediately** (e.g. `prtl_key_...`). It is shown only once and hashed server-side.
7. Note down:
   - `PORTAL_BASE_URL`: e.g., `https://portal.tanmaysinghx.com`
   - `APPLICATION_ID`: UUID from the application table or URL (e.g., `3fa85f64-5717-4562-b3fc-2c963f66afa6`)
   - `PORTAL_APP_KEY`: The API key generated in step 6.

> [!IMPORTANT]
> **API Key Boundary**: The `X-Portal-App-Key` must **never** be shipped in public client-side JavaScript or mobile apps. Always execute these calls from your backend service or agent runtime.

---

## 3. External Auth REST API Reference

All requests to `/api/external/apps/{applicationId}/*` are stateless and exempt from CSRF.

### Headers

| Header | Value | Description |
|---|---|---|
| `Content-Type` | `application/json` | Required for all POST bodies |
| `X-Portal-App-Key` | `<YOUR_PORTAL_APP_KEY>` | The API key generated for the Application |

---

### A. Auto-Login / Verify Credentials

Verifies user credentials against Portal SSO and returns the user identity and platform roles.

- **Endpoint**: `POST /api/external/apps/{applicationId}/login`
- **Request Body**:
```json
{
  "email": "agent.user@example.com",
  "password": "SecurePassword123!"
}
```

- **Success Response (`200 OK`)**:
```json
{
  "authenticated": true,
  "mfaRequired": false,
  "user": {
    "id": "e2d84766-2678-4384-91c7-c7998b6716a5",
    "email": "agent.user@example.com",
    "firstName": "Alex",
    "lastName": "Rivera",
    "roles": ["ROLE_USER"]
  }
}
```

> [!NOTE]
> If `mfaRequired` is `true`, the user has TOTP 2FA enabled on Portal SSO. Programmatic agents should use dedicated agent accounts without 2FA or handle TOTP generation.

- **Failure Responses**:
  - `401 Unauthorized`: Invalid application API key (`X-Portal-App-Key`).
  - `400 Bad Request`: Invalid email/password, account disabled, or account locked.
```json
{
  "error": "PRTL-1002",
  "message": "Invalid email or password."
}
```

---

### B. Programmatic User Registration

Creates a new user account directly in Portal SSO under your application.

- **Endpoint**: `POST /api/external/apps/{applicationId}/register`
- **Request Body**:
```json
{
  "email": "new.user@example.com",
  "password": "StrongPassword123!",
  "firstName": "Taylor",
  "lastName": "Swift"
}
```

- **Field Constraints**:
  - `email`: Required, valid email format, max 255 chars.
  - `password`: Required, between 8 and 100 characters.
  - `firstName`: Optional, max 100 chars.
  - `lastName`: Optional, max 100 chars.

- **Success Response (`201 Created`)**:
```json
{
  "authenticated": true,
  "mfaRequired": false,
  "user": {
    "id": "7b89d412-1234-4567-89ab-cdef01234567",
    "email": "new.user@example.com",
    "firstName": "Taylor",
    "lastName": "Swift",
    "roles": ["ROLE_USER"]
  }
}
```

- **Conflict Response (`409 Conflict`)**:
```json
{
  "error": "PRTL-2001",
  "message": "An account with that email already exists."
}
```

---

## 4. Ready-to-Use Code Implementations

### Python (AI Agent / Backend Service)

```python
import os
import requests
from typing import Optional, Dict, Any

class PortalSSOClient:
    def __init__(self, base_url: str, app_id: str, app_key: str):
        self.base_url = base_url.rstrip("/")
        self.app_id = app_id
        self.headers = {
            "Content-Type": "application/json",
            "X-Portal-App-Key": app_key
        }

    def login(self, email: str, password: str) -> Dict[str, Any]:
        """Authenticate user credentials against Portal SSO."""
        url = f"{self.base_url}/api/external/apps/{self.app_id}/login"
        resp = requests.post(url, json={"email": email, "password": password}, headers=self.headers)
        resp.raise_for_status()
        return resp.json()

    def register(self, email: str, password: str, first_name: str = "", last_name: str = "") -> Dict[str, Any]:
        """Register a new user in Portal SSO."""
        url = f"{self.base_url}/api/external/apps/{self.app_id}/register"
        payload = {
            "email": email,
            "password": password,
            "firstName": first_name,
            "lastName": last_name
        }
        resp = requests.post(url, json=payload, headers=self.headers)
        resp.raise_for_status()
        return resp.json()

    def ensure_authenticated_agent(self, email: str, password: str, name: str = "AI Agent") -> Dict[str, Any]:
        """
        Idempotent Auto-Login Pattern:
        Attempts login; if account does not exist, registers automatically and logs in.
        """
        try:
            auth = self.login(email, password)
            print(f"Logged in as {auth['user']['email']} (ID: {auth['user']['id']})")
            return auth
        except requests.exceptions.HTTPError as e:
            if e.response is not None and e.response.status_code == 400:
                print("Account does not exist or credentials invalid. Attempting registration...")
                try:
                    reg = self.register(email, password, first_name=name)
                    print(f"Registered and logged in as {reg['user']['email']}")
                    return reg
                except requests.exceptions.HTTPError as reg_err:
                    print(f"Registration failed: {reg_err.response.text}")
                    raise
            raise

# Usage Example:
if __name__ == "__main__":
    portal = PortalSSOClient(
        base_url="https://portal.tanmaysinghx.com",
        app_id="YOUR_APPLICATION_UUID",
        app_key="YOUR_PORTAL_APP_KEY"
    )

    session = portal.ensure_authenticated_agent(
        email="bot-agent@mycompany.com",
        password="AgentPassword@2026"
    )
    print("Agent Session Established:", session)
```

---

### Node.js / TypeScript (Next.js, Express, Fastify)

```typescript
export interface PortalUser {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  roles: string[];
}

export interface PortalAuthResponse {
  authenticated: boolean;
  mfaRequired: boolean;
  user: PortalUser | null;
}

export class PortalSSOService {
  private readonly baseUrl: string;
  private readonly appId: string;
  private readonly appKey: string;

  constructor(config: { baseUrl: string; appId: string; appKey: string }) {
    this.baseUrl = config.baseUrl.replace(/\/$/, '');
    this.appId = config.appId;
    this.appKey = config.appKey;
  }

  private get headers(): HeadersInit {
    return {
      'Content-Type': 'application/json',
      'X-Portal-App-Key': this.appKey,
    };
  }

  async login(email: string, password: string): Promise<PortalAuthResponse> {
    const res = await fetch(`${this.baseUrl}/api/external/apps/${this.appId}/login`, {
      method: 'POST',
      headers: this.headers,
      body: JSON.stringify({ email, password }),
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || `Login failed with status ${res.status}`);
    }

    return res.json();
  }

  async register(
    email: string,
    password: string,
    firstName?: string,
    lastName?: string
  ): Promise<PortalAuthResponse> {
    const res = await fetch(`${this.baseUrl}/api/external/apps/${this.appId}/register`, {
      method: 'POST',
      headers: this.headers,
      body: JSON.stringify({ email, password, firstName, lastName }),
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || `Registration failed with status ${res.status}`);
    }

    return res.json();
  }

  /**
   * Auto-login helper: logs in if account exists, otherwise creates it automatically.
   */
  async autoLoginAgent(email: string, password: string, agentName = 'AI Agent'): Promise<PortalUser> {
    try {
      const result = await this.login(email, password);
      if (result.authenticated && result.user) {
        return result.user;
      }
      throw new Error('Authentication returned false or empty user');
    } catch {
      // Attempt registration
      const regResult = await this.register(email, password, agentName);
      if (regResult.authenticated && regResult.user) {
        return regResult.user;
      }
      throw new Error('Failed to auto-register agent account');
    }
  }
}
```

---

### Terminal / cURL Commands

#### 1. Test Login
```bash
curl -X POST "https://portal.tanmaysinghx.com/api/external/apps/YOUR_APP_UUID/login" \
  -H "Content-Type: application/json" \
  -H "X-Portal-App-Key: YOUR_API_KEY" \
  -d '{
    "email": "agent@example.com",
    "password": "Password123!"
  }'
```

#### 2. Test Registration
```bash
curl -X POST "https://portal.tanmaysinghx.com/api/external/apps/YOUR_APP_UUID/register" \
  -H "Content-Type: application/json" \
  -H "X-Portal-App-Key: YOUR_API_KEY" \
  -d '{
    "email": "agent@example.com",
    "password": "Password123!",
    "firstName": "Autonomous",
    "lastName": "Agent"
  }'
```

---

## 5. Standard OAuth 2.1 / OIDC Integration (For Web Apps)

If your app also has human browser visitors who should log in via the unified Single Sign-On screen:

### Standard Endpoints
- **OIDC Discovery**: `/.well-known/openid-configuration`
- **Authorization**: `/oauth2/authorize`
- **Token Exchange**: `/oauth2/token`
- **JWKS Key Set**: `/oauth2/jwks`
- **User Profile**: `/userinfo`

### PKCE Invariant
Portal SSO strictly adheres to OAuth 2.1:
- `code_challenge` and `code_challenge_method=S256` are **mandatory** on all clients (public and confidential).
- Use standard libraries (e.g. `openid-client` in Node, `authlib` in Python, `next-auth`, or `angular-oauth2-oidc`).

---

## 6. Security Checklist for AI Agents

1. **Store Secrets in Environment Variables**:
   - `PORTAL_BASE_URL`
   - `PORTAL_APP_ID`
   - `PORTAL_APP_KEY`
2. **Never commit raw API keys or passwords** into prompt transcripts, agent logs, or public repositories.
3. **Password Complexity**: Ensure passwords are at least 8 characters long (recommend 16+ characters for automated agent accounts).
4. **Idempotent Provisioning**: Use the `ensure_authenticated_agent` pattern so that your agent can recover gracefully even if deployed on a fresh environment.
