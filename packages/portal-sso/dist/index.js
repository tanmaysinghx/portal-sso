import { generateRandomString, generateCodeChallenge, parseJwtPayload } from './pkce';
import { registerPortalButton, PortalLoginButtonElement } from './web-component';
export * from './types';
export { registerPortalButton, PortalLoginButtonElement };
// Auto-register custom element when loaded in browser
if (typeof window !== 'undefined') {
    registerPortalButton();
}
/**
 * Portal SSO JavaScript & TypeScript Client SDK.
 * Zero-dependency, lightweight client for OAuth 2.1 & OIDC authentication with built-in popup support.
 */
export class PortalSSO {
    portalUrl;
    clientId;
    redirectUri;
    scope;
    storageKey;
    listeners = new Set();
    constructor(config) {
        if (!config || !config.clientId) {
            throw new Error('[PortalSSO] "clientId" is required');
        }
        const defaultOrigin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8080';
        this.portalUrl = (config.portalUrl || defaultOrigin).replace(/\/$/, '');
        this.clientId = config.clientId;
        this.redirectUri = config.redirectUri || `${this.portalUrl}/oauth2/popup-callback`;
        this.scope = config.scope || 'openid profile email';
        this.storageKey = config.storageKey || 'portal_sso_session';
    }
    /**
     * Opens an Okta-style popup login window for user authentication.
     * Completes OAuth 2.1 + PKCE (S256), extracts claims, saves session, and resolves.
     */
    async loginWithPopup(options) {
        if (typeof window === 'undefined') {
            throw new Error('[PortalSSO] loginWithPopup can only be called in a browser environment');
        }
        const width = options?.width || 500;
        const height = options?.height || 680;
        const left = window.screenX + (window.outerWidth - width) / 2;
        const top = window.screenY + (window.outerHeight - height) / 2;
        const codeVerifier = generateRandomString(64);
        const codeChallenge = await generateCodeChallenge(codeVerifier);
        const state = generateRandomString(32);
        const authUrl = `${this.portalUrl}/oauth2/authorize?` +
            `response_type=code` +
            `&client_id=${encodeURIComponent(this.clientId)}` +
            `&redirect_uri=${encodeURIComponent(this.redirectUri)}` +
            `&scope=${encodeURIComponent(this.scope)}` +
            `&code_challenge=${encodeURIComponent(codeChallenge)}` +
            `&code_challenge_method=S256` +
            `&state=${encodeURIComponent(state)}`;
        const popup = window.open(authUrl, `portal_sso_${Date.now()}`, `width=${width},height=${height},left=${left},top=${top},menubar=no,toolbar=no,status=no`);
        if (!popup) {
            throw new Error('[PortalSSO] Popup blocked. Please allow popups for this site.');
        }
        return new Promise((resolve, reject) => {
            let interval = null;
            const cleanup = () => {
                if (interval)
                    clearInterval(interval);
                window.removeEventListener('message', messageHandler);
            };
            const messageHandler = (event) => {
                if (!event.data || event.data.type !== 'PORTAL_SSO_POPUP_CALLBACK') {
                    return;
                }
                const data = event.data;
                if (data.state !== state) {
                    cleanup();
                    reject(new Error('[PortalSSO] State mismatch. Possible CSRF attack detected.'));
                    return;
                }
                if (data.error) {
                    cleanup();
                    reject(new Error(`[PortalSSO] ${data.errorDescription || data.error}`));
                    return;
                }
                if (data.code) {
                    cleanup();
                    this.exchangeCodeForTokens(data.code, codeVerifier, this.redirectUri)
                        .then((session) => {
                        this.notifyListeners(session.user);
                        resolve(session);
                    })
                        .catch((err) => reject(err));
                }
            };
            window.addEventListener('message', messageHandler);
            interval = setInterval(() => {
                if (popup.closed) {
                    cleanup();
                    reject(new Error('[PortalSSO] Authentication window was closed by the user'));
                }
            }, 700);
        });
    }
    /**
     * Triggers a standard full-page redirect to Portal SSO.
     */
    async loginWithRedirect(redirectUri) {
        if (typeof window === 'undefined')
            return;
        const targetRedirectUri = redirectUri || window.location.href;
        const codeVerifier = generateRandomString(64);
        const codeChallenge = await generateCodeChallenge(codeVerifier);
        const state = generateRandomString(32);
        sessionStorage.setItem('portal_pkce_verifier', codeVerifier);
        sessionStorage.setItem('portal_oauth_state', state);
        sessionStorage.setItem('portal_redirect_uri', targetRedirectUri);
        window.location.href =
            `${this.portalUrl}/oauth2/authorize?` +
                `response_type=code` +
                `&client_id=${encodeURIComponent(this.clientId)}` +
                `&redirect_uri=${encodeURIComponent(targetRedirectUri)}` +
                `&scope=${encodeURIComponent(this.scope)}` +
                `&code_challenge=${encodeURIComponent(codeChallenge)}` +
                `&code_challenge_method=S256` +
                `&state=${encodeURIComponent(state)}`;
    }
    /**
     * Processes the authorization code callback from a full-page redirect.
     */
    async handleRedirectCallback() {
        if (typeof window === 'undefined')
            return null;
        const urlParams = new URLSearchParams(window.location.search);
        const code = urlParams.get('code');
        const state = urlParams.get('state');
        const error = urlParams.get('error');
        if (error) {
            throw new Error(`[PortalSSO] ${urlParams.get('error_description') || error}`);
        }
        if (!code || !state)
            return null;
        const savedState = sessionStorage.getItem('portal_oauth_state');
        const codeVerifier = sessionStorage.getItem('portal_pkce_verifier');
        const redirectUri = sessionStorage.getItem('portal_redirect_uri') || window.location.href.split('?')[0];
        if (!savedState || state !== savedState || !codeVerifier) {
            throw new Error('[PortalSSO] Invalid state or missing PKCE verifier during callback');
        }
        sessionStorage.removeItem('portal_oauth_state');
        sessionStorage.removeItem('portal_pkce_verifier');
        sessionStorage.removeItem('portal_redirect_uri');
        const session = await this.exchangeCodeForTokens(code, codeVerifier, redirectUri);
        this.notifyListeners(session.user);
        return session;
    }
    /**
     * Exchanges authorization code for tokens using PKCE verifier.
     */
    async exchangeCodeForTokens(code, codeVerifier, redirectUri) {
        const tokenEndpoint = `${this.portalUrl}/oauth2/token`;
        const body = new URLSearchParams();
        body.append('grant_type', 'authorization_code');
        body.append('client_id', this.clientId);
        body.append('code', code);
        body.append('code_verifier', codeVerifier);
        body.append('redirect_uri', redirectUri);
        const res = await fetch(tokenEndpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: body.toString(),
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.error_description || err.error || 'Failed to exchange authorization code');
        }
        const data = await res.json();
        const idClaims = data.id_token ? parseJwtPayload(data.id_token) : {};
        const accessClaims = data.access_token ? parseJwtPayload(data.access_token) : {};
        const user = {
            id: idClaims.sub || accessClaims.sub || '',
            email: idClaims.email || accessClaims.email || '',
            name: idClaims.name || idClaims.preferred_username || '',
            roles: idClaims.roles || accessClaims.roles || [],
            rawClaims: idClaims,
        };
        const session = {
            user,
            accessToken: data.access_token,
            idToken: data.id_token,
            refreshToken: data.refresh_token,
            expiresIn: data.expires_in,
            expiresAt: Date.now() + (data.expires_in || 900) * 1000,
        };
        try {
            localStorage.setItem(this.storageKey, JSON.stringify(session));
        }
        catch { }
        return session;
    }
    /**
     * Returns current user if logged in and tokens are not expired.
     */
    getCurrentUser() {
        const session = this.getSession();
        return session ? session.user : null;
    }
    /**
     * Returns active session object or null.
     */
    getSession() {
        if (typeof window === 'undefined')
            return null;
        try {
            const raw = localStorage.getItem(this.storageKey);
            if (!raw)
                return null;
            const session = JSON.parse(raw);
            if (session.expiresAt && Date.now() > session.expiresAt) {
                this.logout();
                return null;
            }
            return session;
        }
        catch {
            return null;
        }
    }
    /**
     * Returns current access token or null if unauthenticated.
     */
    getAccessToken() {
        const session = this.getSession();
        return session ? session.accessToken : null;
    }
    /**
     * Logs out locally and clears stored session.
     */
    logout() {
        if (typeof window !== 'undefined') {
            try {
                localStorage.removeItem(this.storageKey);
            }
            catch { }
        }
        this.notifyListeners(null);
    }
    /**
     * Subscribe to authentication changes (login, logout, session expiration).
     */
    onAuthStateChange(callback) {
        this.listeners.add(callback);
        return () => this.listeners.delete(callback);
    }
    notifyListeners(user) {
        this.listeners.forEach((cb) => {
            try {
                cb(user);
            }
            catch (e) {
                console.error('[PortalSSO] Error in auth listener callback:', e);
            }
        });
    }
}
//# sourceMappingURL=index.js.map