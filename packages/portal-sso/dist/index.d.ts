import { registerPortalButton, PortalLoginButtonElement } from './web-component';
import type { PortalSSOConfig, PortalSession, PortalUser, PopupOptions, AuthStateCallback } from './types';
export * from './types';
export { registerPortalButton, PortalLoginButtonElement };
/**
 * Portal SSO JavaScript & TypeScript Client SDK.
 * Zero-dependency, lightweight client for OAuth 2.1 & OIDC authentication with built-in popup support.
 */
export declare class PortalSSO {
    readonly portalUrl: string;
    readonly clientId: string;
    readonly redirectUri: string;
    readonly scope: string;
    readonly storageKey: string;
    private listeners;
    constructor(config: PortalSSOConfig);
    /**
     * Opens an Okta-style popup login window for user authentication.
     * Completes OAuth 2.1 + PKCE (S256), extracts claims, saves session, and resolves.
     */
    loginWithPopup(options?: PopupOptions): Promise<PortalSession>;
    /**
     * Triggers a standard full-page redirect to Portal SSO.
     */
    loginWithRedirect(redirectUri?: string): Promise<void>;
    /**
     * Processes the authorization code callback from a full-page redirect.
     */
    handleRedirectCallback(): Promise<PortalSession | null>;
    /**
     * Exchanges authorization code for tokens using PKCE verifier.
     */
    exchangeCodeForTokens(code: string, codeVerifier: string, redirectUri: string): Promise<PortalSession>;
    /**
     * Returns current user if logged in and tokens are not expired.
     */
    getCurrentUser(): PortalUser | null;
    /**
     * Returns active session object or null.
     */
    getSession(): PortalSession | null;
    /**
     * Returns current access token or null if unauthenticated.
     */
    getAccessToken(): string | null;
    /**
     * Logs out locally and clears stored session.
     */
    logout(): void;
    /**
     * Subscribe to authentication changes (login, logout, session expiration).
     */
    onAuthStateChange(callback: AuthStateCallback): () => void;
    private notifyListeners;
}
//# sourceMappingURL=index.d.ts.map